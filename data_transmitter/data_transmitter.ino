#include <Wire.h>
#include <SoftwareSerial.h>
#include <math.h>

//////////////////////////////////////////////////
// GENERAL PURPOSE DECLARATIONS AND DEFINITIONS //
//////////////////////////////////////////////////
#define SIZEOF_SHORT 2
#define SIZEOF_LONG 4

// powers of two
// these macros are defined so we replace repetitive calls to pow(2, x), which take a lot of time
#define TWO_POW_2 4
#define TWO_POW_4 16
#define TWO_POW_8 256
#define TWO_POW_11 2048
#define TWO_POW_12 4096
#define TWO_POW_13 8192
#define TWO_POW_15 32768
#define TWO_POW_16 65536

///////////////////////////////////////////////////////////////
// MQ-4/MQ-7 AND HR202 RELATED DECLARATIONS AND DEFINITIONS //
/////////////////////////////////////////////////////////////

#define CO_TYPE 7
#define CH4_TYPE 4

#define CH4_SENSOR_PIN A2
#define CO_SENSOR_PIN A3
#define HUMIDITY_SENSOR_PIN A6

#define RL 1000                                                       // Ohm; Load resistance on the MQ breakout boards

#define convert_to_voltage(value) ((float) (value * 5.0 / 1023.0))    // converts an integer between 0 and 1023 into a double between 0 and 5

#define MAX_READING_HISTORY 10                                        // used for setting how many previous readings should be recorded

/**
 * @brief Data structure for MQ breakout boards
 */
typedef struct MQ
{
  uint8_t pin;                                                        // the pin the board is connected to
  uint8_t type;                                                       // type of sensor installed on board; 7 - CO, 4 - CH4    
  float r0;                                                           // sensor resistance in clean air
  float coef_a;                                                       // used in calculations of real ppm
  float coef_b;                                                       // used in calculation of real ppm
  uint16_t prev_readings[MAX_READING_HISTORY];                        // stores the previous readings
  uint8_t prev_reading_index;                                         // index of latest entry in array
  bool initial_sequence_done;                                         // if the previous reading array is not filled yet, we must ignore the 0's inside when averaging
} MQ;

typedef struct HUM
{
  uint16_t prev_readings_hum[MAX_READING_HISTORY];
  uint8_t prev_reading_index;
  bool initial_sequence_done;
}HUM;

// Variables for each used sensor
MQ co, ch4;
HUM humidity;

/**
 * @brief Calculates the value of the resistance in clean air.
 * This function is called when the node is powered up.
 * It assumes that the sensor is in clean air.
 * If not in clean air, or the values look weird, 
 * reset the Arduino using the on-board button
 * 
 * @param sensor the sensor structure
 * @param clean_air_ratio RS/R0 in clean air, according to the datasheet
 * @return double the value of r0
 **/
double get_r0(const MQ sensor, const uint8_t clean_air_ratio)
{
  uint16_t raw_value = analogRead(sensor.pin);                          // read the raw data from the sensor                    
  float voltage = convert_to_voltage(raw_value);                        // convert the read value to a voltage
  float r0 = (((5.0 * RL) / voltage) - RL) / clean_air_ratio;           // derived from the voltage divider formula; clean_air_ration only needed here
  return r0;
}

/**
 * @brief Initializes the MQ structure.
 */
void init_mq(MQ* sensor, const uint8_t pin, const uint8_t type, const float coef_a, const float coef_b)
{
  sensor->pin = pin;
  sensor->type = type;
  sensor->coef_a = coef_a;
  sensor->coef_b = coef_b;
  
  if (type == CO_TYPE)
  {
    sensor->r0 = get_r0(*sensor, 10);
  }
  else
  {
    sensor->r0 = get_r0(*sensor, 1);
  }

  for (uint8_t i = 0; i < MAX_READING_HISTORY; i++)
  {
    sensor->prev_readings[i] = 0;
  }
  
  sensor->prev_reading_index = 0;
  sensor->initial_sequence_done = false;
}
void init_hum(HUM* sensor)
{

  for (uint8_t i = 0; i < MAX_READING_HISTORY; i++)
  {
    sensor->prev_readings_hum[i] = 0;
  }
  
  sensor->prev_reading_index = 0;
  sensor->initial_sequence_done = false;
}

/**
 * @brief Calculates and returns the actual ppm value for the specified sensor.
 * Not used because it is subject to transient errors,
 * but it is still good to have it around
 */
double get_ppm(const MQ sensor)
{
  uint16_t raw_value = analogRead(sensor.pin);                          // read the raw data from the sensor
  float voltage = convert_to_voltage(raw_value);                        // convert the read value to a voltage
  float rs = ((5.0 * RL) / voltage) - RL;                               // derived from the voltage divider formula
  float ratio = rs / sensor.r0;                                         // calculate the resistance ratio
  return sensor.coef_a * pow(ratio, sensor.coef_b);                     // plug the ratio in the exponential function with set coefficients
}

/**
 * @brief Calculates and returns the averaged ppm value for the last MAX_READING_HISTORY readings.
 * Much like get_ppm(), but it also uses the previous readings' array,
 * and it also averages the values found there
 * 
 * By using get_averaged_ppm we get more stable values
 */
double get_averaged_ppm(MQ* sensor)
{           
  uint16_t crt_reading = analogRead(sensor->pin);

  sensor->prev_readings[sensor->prev_reading_index++] = crt_reading;
  if (sensor->prev_reading_index == MAX_READING_HISTORY)
  {
    sensor->initial_sequence_done = true;
  }
  sensor->prev_reading_index %= MAX_READING_HISTORY;

  float reading_average = 0;
  for (uint8_t i = 0; i < MAX_READING_HISTORY; i++)
  {
    reading_average += sensor->prev_readings[i];
  }
  reading_average /= (sensor->initial_sequence_done ? MAX_READING_HISTORY : sensor->prev_reading_index);

  // convert the reading average to a ppm value
  float voltage = convert_to_voltage(reading_average);
  float rs = ((5.0 * RL) / voltage) - RL;
  float ratio = rs / sensor->r0;
  return sensor->coef_a * pow(ratio, sensor->coef_b);
}

double get_averaged_hum(HUM* sensor)
{           
  uint16_t crt_reading = analogRead(HUMIDITY_SENSOR_PIN);

  sensor->prev_readings_hum[sensor->prev_reading_index++] = crt_reading;
  if (sensor->prev_reading_index == MAX_READING_HISTORY)
  {
    sensor->initial_sequence_done = true;
  }
  sensor->prev_reading_index %= MAX_READING_HISTORY;

  float reading_average = 0;
  for (uint8_t i = 0; i < MAX_READING_HISTORY; i++)
  {
    reading_average += sensor->prev_readings_hum[i];
  }
  reading_average /= (sensor->initial_sequence_done ? MAX_READING_HISTORY : sensor->prev_reading_index);

  // convert the reading average to a % value
  float res = ((95-(95-20)/1023)*reading_average)/1000;
  return res;
}

/////////////////////////////////////////////////
// BMP180 RELATED DECLARATIONS AND DEFINITIONS //
/////////////////////////////////////////////////

// BMP180 specific macros
#define BMP180_I2C_ADDR 0x77
#define BMP180_COMMAND_REGISTER_ADDRESS 0xF4
#define BMP180_STATUS_REGISTER_ADDRESS 0xF6
#define BMP180_COMMAND_TEMPERATURE 0x2E
// 0x34 + (oss << 6), where oss = [0, 3]
#define BMP180_COMMAND_PRESSURE0 0x34
#define BMP180_COMMAND_PRESSURE1 0x74
#define BMP180_COMMAND_PRESSURE2 0xB4
#define BMP180_COMMAND_PRESSURE3 0xF4

// variables for calibration data of the BMP180
int16_t ac1, ac2, ac3, b1, b2, mb, mc, md;
uint16_t ac4, ac5, ac6;

/**
 * @brief Reads bytes from the specified register address of the BMP180 module 
 * using I2C. The value read will be stored at the address pointed 
 * by the second parameter.
 * 
 * @param start_addr the source address
 * @param values the destination address
 * @param len number of bytes
 * @return int 0 if ok, -1 if error
 */
int read_bytes(const byte start_addr, byte* values, const size_t len)
{
  // send the address of the register you wish to read from
  Wire.beginTransmission(BMP180_I2C_ADDR);
  Wire.write(start_addr);
  int _error = Wire.endTransmission();

  if (_error == 0)
  {
    Wire.requestFrom(BMP180_I2C_ADDR, len);
    
    // wait for data to be available
    while (Wire.available() < len);
    // read the bytes
    for (size_t i = 0; i < len; i++)
    {
      values[i] = Wire.read();
    }
    return 0;
  }

  return -1;
}

/**
 * @brief Wrapper of read_bytes() for reading short values from the BMP180 module.
 * 
 * @param start_addr the source address
 * @param value the destination address - pointer to a short variable 
 * represented as int16_t
 * @return int 0 if ok, -1 if error
 */
int read_short(const byte start_addr, int16_t* value)
{
  byte byte_value[SIZEOF_SHORT];
  int _error = read_bytes(start_addr, byte_value, SIZEOF_SHORT);

  if (_error == -1)
  {
    return -1;
  }
  else
  {
    *value = (int16_t) (byte_value[0] << 8 | byte_value[1]);
    return 0;
  }
}

/**
 * @brief Wrapper of read_bytes() for reading unsigned short values from the BMP180 module.
 * 
 * @param start_addr the source address
 * @param value the destination address - pointer to an unsigned short variable 
 * represented as uint16_t
 * @return int 0 if ok, -1 if error
 */
int read_ushort(const byte start_addr, uint16_t* value)
{
  byte byte_value[SIZEOF_SHORT];
  int _error = read_bytes(start_addr, byte_value, SIZEOF_SHORT);

  if (_error == -1)
  {
    return -1;
  }
  else
  {
    // swaps bytes
    *value = ((uint16_t) byte_value[0] << 8 | (uint16_t) byte_value[1]);
    return 0;
  }
}

/**
 * @brief Wrapper of read_bytes() for reading long values 
 * from the BMP180 module.
 * 
 * @param start_addr the source address
 * @param value the destination address - pointer to a long variable 
 * represented as int32_t
 * @return int 0 if ok, -1 if error
 */
int read_long(const byte start_addr, uint32_t* value) // !!
{
  byte byte_value[SIZEOF_LONG];
  int _error = read_bytes(start_addr, byte_value, SIZEOF_LONG);

  if (_error == -1)
  {
    return -1;
  }
  else
  {
    *value = 0;
    for (size_t i = 0; i < 4; i++)
    {
      *value = *value | (int32_t) byte_value[i] << (8 * (SIZEOF_LONG - i - 1));
    }
    return 0;
  }
}

/**
 * @brief Wrapper of read_bytes() for reading unsigned long values 
 * from the BMP180 module.
 * 
 * @param start_addr the source address
 * @param value the destination address - pointer to an unsigned long variable 
 * represented as uint32_t
 * @return int 0 if ok, -1 if error
 */
int read_ulong(const byte start_addr, uint32_t* value)
{
  byte byte_value[SIZEOF_LONG];
  int _error = read_bytes(start_addr, byte_value, SIZEOF_LONG);

  if (_error == -1)
  {
    return -1;
  }
  else
  {
    *value = 0;
    for (size_t i = 0; i < 4; i++)
    {
      *value = *value | (uint32_t) byte_value[i] << (8 * (SIZEOF_LONG - i - 1));
    }
    return 0;
  }
}

/**
 * @brief Writes bytes to the specified register address of the BMP180 module 
 * using I2C. The value to be written will be taken from the address pointed 
 * by the second parameter.
 * 
 * @param start_addr the destination address
 * @param values the source address
 * @param len number of bytes
 * @return int 0 if ok, -1 if error
 */
int write_bytes(const byte start_addr, const byte* value, const size_t len)
{
  Wire.beginTransmission(BMP180_I2C_ADDR);
  Wire.write(&start_addr, 1);
  Wire.write(value, len);
  int _error = Wire.endTransmission();
  if (_error == 0)
  {
    return 0;
  }

  return -1;
}

/**
 * @brief Reads all the calibration constants needed 
 * for temperature and pressure calculation from the ROM of the BMP180.
 * 
 * @return int 0 if all reads were successful, -1 if not
 */
int read_calibration_data()
{
  if (read_short(0xAA, &ac1) == -1 || 
      read_short(0xAC, &ac2) == -1 ||
      read_short(0xAE, &ac3) == -1 ||
      read_ushort(0xB0, &ac4) == -1 ||
      read_ushort(0xB2, &ac5) == -1 ||
      read_ushort(0xB4, &ac6) == -1 ||
      read_short(0xB6, &b1) == -1 ||
      read_short(0xB8, &b2) == -1 ||
      read_short(0xBA, &mb) == -1 ||
      read_short(0xBC, &mc) == -1 ||
      read_short(0xBE, &md) == -1
  )
  {
    // failed to read constants
    return -1;
  }

  return 0;
  
}

/**
 * @brief Reads the raw temperature reading from the BMP180.
 * 
 * @return int32_t 0 if all the operations were successful, 
 * and we have a raw temperature reading, -1 if not
 */
int32_t read_uncompensated_temperature_value()
{
  int32_t ut;
  byte byte_to_send = 0x2E;

  int _error = write_bytes(BMP180_COMMAND_REGISTER_ADDRESS, &byte_to_send, 1);

  if (_error == -1)
  {
    return -1;
  }

  delay(5);

  byte received_bytes[2];
  _error = read_bytes(BMP180_STATUS_REGISTER_ADDRESS, received_bytes, 2);

  if (_error == -1)
  {
    return -1;
  }

  ut = 0;
  ut = (uint32_t)received_bytes[0] << 8 | (uint32_t)received_bytes[1];

  return ut;
}

/**
 * @brief Reads the raw temperature reading from the BMP180. 
 * Must be called after read_uncompensated_temperature_value()
 * 
 * @param oss Oversampling setting for pressure reading. 
 * Refer to the datasheet of the module.
 * @return int32_t 0 if all the operations were successful,
 * and we have a raw pressure reading, -1 if not
 */
int32_t read_uncompensated_pressure_value(uint8_t oss)
{
  int32_t up = 0;
  byte byte_to_send;
  uint8_t delay_period;
  switch (oss)
  {
    case 0:
    {
      byte_to_send = BMP180_COMMAND_PRESSURE0;
      delay_period = 5;
      break;
    }
    case 1:
    {
      byte_to_send = BMP180_COMMAND_PRESSURE1;
      delay_period = 8;
      break;
    }
    case 2:
    {
      byte_to_send = BMP180_COMMAND_PRESSURE2;
      delay_period = 14;
      break;
    }
    case 3:
    {
      byte_to_send = BMP180_COMMAND_PRESSURE3;
      delay_period = 26;
      break;
    }
    default:
    {
      return -1;
    }
  }

  int _error = write_bytes(BMP180_COMMAND_REGISTER_ADDRESS, &byte_to_send, 1);
  delay(delay_period);

  if (_error == -1)
  {
    return -1;
  }

  byte received_bytes[3];
  _error = read_bytes(BMP180_STATUS_REGISTER_ADDRESS, received_bytes, 3);
  
  if (_error == -1)
  {
    return -1;
  }
  up = ((uint32_t)received_bytes[0] << 16 | (uint32_t)received_bytes[1] << 8 | (uint32_t)received_bytes[2]) >> (8 - oss);
  return up;
}

/**
 * @brief Computes the true temperature value 
 * based on a raw temperature reading. 
 * Must be called after read_uncompensated_*().
 * For the mathematical formulas, refer to the datasheet of the module.
 * 
 * @param ut The uncompensated/raw temperature reading
 * @param b5 Pointer to a numerical value needed 
 * for both true temperature and true pressure calculation. 
 * Because of this, it must be considered a parameter and not a local variable
 * @return int32_t The true temperature in 0.1 degrees C
 */
int32_t calculate_true_temperature(int32_t ut, int32_t* b5)
{
  int32_t x1 = (ut - ac6) * ac5 / TWO_POW_15;
  int32_t x2 = (int32_t)mc * TWO_POW_11 / (x1 + md); // cast the first because it might overflow from its datatype
  *b5 = x1 + x2;
  int32_t t = (*b5 + 8) / TWO_POW_4;
  return t;
}

/**
 * @brief Computes the true temperature value
 * based on a raw temperature reading. 
 * Must be called after read_uncompensated_*() and calculate_true_temperature().
 * For the mathematical formulas, refer to the datasheet of the module.
 * 
 * @param up The uncompensated/raw pressure reading
 * @param oss Oversampling setting for pressure reading.
 * Refer to the datasheet of the module. 
 * @param b5 Numerical value needed 
 * for both true temperature and true pressure calculation.
 * Because of this, it must be considered a parameter and not a local variable
 * @return int32_t The true pressure in hPa
 */
int32_t calculate_true_pressure(int32_t up, int32_t oss, int32_t b5)
{
  int32_t b6 = b5 - 4000;
  int32_t x1 = (b2 * ((int64_t)b6 * (int64_t)b6 / TWO_POW_12)) / TWO_POW_11;
  int32_t x2 = ac2 * b6 / TWO_POW_11;
  int32_t x3 = x1 + x2;
  int32_t b3 = ((((uint32_t)ac1 * 4 + (uint32_t)x3) << oss) + 2) / 4; // always cast to unsigned before shifting
  x1 = ac3 * b6 / TWO_POW_13;
  x2 = (b1 * (b6 * b6 / TWO_POW_12)) / TWO_POW_16;
  x3 = ((x1 + x2) + 2) / 4;
  uint32_t b4 = ac4 * (uint32_t)(x3 + 32768) / TWO_POW_15;
  uint32_t b7 = ((uint32_t)up - b3) * ((uint32_t)50000 >> oss);

  int32_t p;
  if (b7 < 0x80000000)
  {
    p = (b7 * 2) / b4;
  }
  else 
  {
    p = (b7 / b4) * 2;
  }
  x1 = (p / TWO_POW_8) * (p / TWO_POW_8);
  x1 = (x1 * 3038) / TWO_POW_16;
  x2 = (-7357 * p) / TWO_POW_16;
  p = p + (x1 + x2 + 3791) / TWO_POW_4;
  return p;
}

/**
 * @brief Calls the read_uncompensated_*() and calculate_true_*() functions 
 * in the correct order and outputs the temperature and pressure 
 * through one single convenient call. 
 * Use this to get what you need from the BMP180 sensor.
 * 
 * @param p Address where the true pressure will be stored
 * @param t Address where the true temperature will be stored
 * @return int32_t 0 if ok, -1 if error
 */
int32_t get_bmp_values(int32_t* p, int32_t* t)
{
  int32_t ut = read_uncompensated_temperature_value();

  if (ut == -1)
  {
    return -1;
  }

  uint8_t oss = 3;
  int32_t up = read_uncompensated_pressure_value(oss);
  

  if (up == -1)
  {
    return -1;
  }

  int32_t b5 = 0;
  *t = calculate_true_temperature(ut, &b5);

  *p = calculate_true_pressure(up, oss, b5);

  return 0;
}

////////////////////////////////////////////////
// HC-05 RELATED DECLARATIONS AND DEFINITIONS //
////////////////////////////////////////////////

// Bluetooth Serial interface specific macros
#define BT_SERIAL_TX_PIN 2
#define BT_SERIAL_RX_PIN 3

// Object instantiation for the software-emulated serial interface
SoftwareSerial BTSerial(BT_SERIAL_RX_PIN, BT_SERIAL_TX_PIN);

/**
 * @brief Data structure used for transferring 
 * data fetched from the sensors to the HC-05.
 * The sensors will output 4 int32_t values 
 * through the use of the previous functions.
 * These values will be transferred to the HC-05 (bluetooth module) 
 * as an array of 16 bytes.
 * 
 */
typedef union {
  struct {
    int32_t temperature;
    int32_t pressure;
    int32_t ch4_ppm;
    int32_t co_ppm;
    int32_t hum;
  } values;
  byte bin[20];
} payload;

/**
 * @brief Conversion function for the payload. 
 * Use this if the receiving Bluetooth device is based on a different endianess
 * than the Arduino Nano. (i.e. Android phones)
 * 
 * @param msg Pointer to the union holding the data to be sent
 */
void convert(payload* msg);
 
void convert(payload* msg)
{
  byte* aux = new byte(20);
  memcpy(aux, msg->bin, (size_t) 20);

  for (int i = 0; i < 5; i++)
  {
    for (int j = 0; j < 4; j++)
    {
      msg->bin[4 * i + j] = aux[4 * i + 3 - j];
    }
  }
  
  delete(aux);
}

/////////////////////////////////////////////
// ARDUINO IDE FRAMEWORK RELATED FUNCTIONS //
/////////////////////////////////////////////

void setup() {
  Wire.begin();
  Serial.begin(9600);
  Serial.println("Waiting for current stabilization throughout the circuit...");
  delay(2000);
  Serial.println("Initializing HC-05...");
  BTSerial.begin(9600);
  
  // Init BMP-180
  Serial.println("Initializing BMP-180...");
  if (read_calibration_data() == -1)
  {
    Serial.println("Failed to initialize BMP-180. Please reset.");
    while(1);
  }
  
  // Init the gas sensors
  Serial.println("Initializing MQ-7...");
  init_mq(&co, CO_SENSOR_PIN, CO_TYPE, 611.082, -2.1048776); // co
  Serial.print("MQ-7 R0 = ");
  Serial.println(co.r0);
  Serial.println("Initializing MQ-4...");
  init_mq(&ch4, CH4_SENSOR_PIN, CH4_TYPE, 38.9835, -1.409119); // ch4
  Serial.print("MQ-4 R0 = ");
  Serial.println(ch4.r0);
  init_hum(&humidity);
}


void loop() {
  int32_t ret_val;
  payload data_to_send;

  // Because the floating point format of Arduino and Android are not always overlapping (experimentally deduced),
  // integers were used for the data transfer, which are converted to floats on destination
  
  // Get the pressure and temperature values
  ret_val = get_bmp_values(&data_to_send.values.pressure, &data_to_send.values.temperature);

  // Get the gas concentration values and humidity
  float ch4_averaged_ppm = get_averaged_ppm(&ch4);
  float co_averaged_ppm = get_averaged_ppm(&co);
  float hum_averaged_value = get_averaged_hum(&humidity);
  data_to_send.values.ch4_ppm = (int32_t) (ch4_averaged_ppm * 100);
  data_to_send.values.co_ppm = (int32_t) (co_averaged_ppm * 100);
  data_to_send.values.hum = (int32_t) (hum_averaged_value * 100);

  if (ret_val != -1)
  { 
    // For debugging
    Serial.print("Temperature:\t");
    Serial.print((float) data_to_send.values.temperature / 10);
    Serial.print("C\n");
    Serial.print("Pressure:\t");
    Serial.print((float) data_to_send.values.pressure / 100);
    Serial.print("hPa\n");
    Serial.print("CH4:\t\t");
    Serial.print((float) data_to_send.values.ch4_ppm / 100);
    Serial.print("ppm\n");
    Serial.print("CO:\t\t");
    Serial.print((float) data_to_send.values.co_ppm / 100);
    Serial.print("ppm\n");
    Serial.print("Humidity:\t\t");
    Serial.print((float) data_to_send.values.hum / 100);
    Serial.print("%\n");
    Serial.print("\n");

    // If valid numbers were read, convert the data and send it to the HC-05
    convert(&data_to_send);
    BTSerial.write(data_to_send.bin, 20);
  }

  delay(3000);
}
