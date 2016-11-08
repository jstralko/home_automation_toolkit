/*********************************************************************
 This is an example for our nRF51822 based Bluefruit LE modules

 Pick one up today in the adafruit shop!

 Adafruit invests time and resources providing this open source code,
 please support Adafruit and open-source hardware by purchasing
 products from Adafruit!

 MIT license, check LICENSE for more information
 All text above, and the splash screen below must be included in
 any redistribution
*********************************************************************/

#include <string.h>
#include <Arduino.h>
#include <SPI.h>
#if not defined (_VARIANT_ARDUINO_DUE_X_) && not defined (_VARIANT_ARDUINO_ZERO_)
  #include <SoftwareSerial.h>
#endif
#include <Wire.h>
#include <Adafruit_NeoPixel.h>

#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "Adafruit_VCNL4010.h"
#include "Adafruit_TCS34725.h"
#include "BluefruitConfig.h"

/*=========================================================================
    APPLICATION SETTINGS

    FACTORYRESET_ENABLE       Perform a factory reset when running this sketch
   
                              Enabling this will put your Bluefruit LE module
                              in a 'known good' state and clear any config
                              data set in previous sketches or projects, so
                              running this at least once is a good idea.
   
                              When deploying your project, however, you will
                              want to disable factory reset by setting this
                              value to 0.  If you are making changes to your
                              Bluefruit LE device via AT commands, and those
                              changes aren't persisting across resets, this
                              is the reason why.  Factory reset will erase
                              the non-volatile memory where config data is
                              stored, setting it back to factory default
                              values.
       
                              Some sketches that require you to bond to a
                              central device (HID mouse, keyboard, etc.)
                              won't work at all with this feature enabled
                              since the factory reset will clear all of the
                              bonding data stored on the chip, meaning the
                              central device won't be able to reconnect.
    PIN                       Which pin on the Arduino is connected to the NeoPixels?
    NUMPIXELS                 How many NeoPixels are attached to the Arduino?
    -----------------------------------------------------------------------*/
    #define FACTORYRESET_ENABLE     0
    #define PIN                     11
    #define NUMPIXELS               90
    #define PIXEL_TYPE              NEO_GRB + NEO_KHZ800
    #define TCS_LED_PIN             5       /* The digital pin connected to the TCS color sensor pin.
                                              Will control turning the sensor's LED on and off */

    #define PROXIMITY_THRESHOLD     10000   /* The Threshold value to conside an object near and
                                                attempt to read its color */
    #define ANIMATION_PERIOD_MS     300


    #define GAMMATABLE_INDEX(rgb, c)       ((int)(((float)rgb / (float)c) * 255.0))

    #ifdef DEBUG
      #define DEBUG_PRINT(x)  Serial.println (x)
      #define DEBUG_PRINT_INLINE(x)  Serial.print (x)
    #else
      #define DEBUG_PRINT(x)
      #define DEBUG_PRINT_INLINE(x)
    #endif

/*=========================================================================*/

Adafruit_NeoPixel pixel = Adafruit_NeoPixel(NUMPIXELS, PIN, PIXEL_TYPE);
Adafruit_TCS34725 tcs = Adafruit_TCS34725(TCS34725_INTEGRATIONTIME_50MS, TCS34725_GAIN_4X);
Adafruit_VCNL4010 vcnl = Adafruit_VCNL4010();
/* ...hardware SPI, using SCK/MOSI/MISO hardware SPI pins and then user selected CS/IRQ/RST */
Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

// A small helper
void error(const __FlashStringHelper*err) {
  DEBUG_PRINT(err);
  while (1);
}

// function prototypes over in packetparser.cpp
uint8_t readPacket(Adafruit_BLE *ble, uint16_t timeout);
float parsefloat(uint8_t *buffer);
void printHex(const uint8_t * data, const uint32_t numBytes);

// the packet buffer
extern uint8_t packetbuffer[];

//Build a gamma correction table for better color accuracy
//Borrowed from TCS Library examples
uint8_t gammatable[256];
int r = 255;
int g = 0;
int b = 0;

/**************************************************************************/
/*!
    @brief  Sets up the HW an the BLE module
*/
/**************************************************************************/
void setup(void)
{

#ifdef DEBUG
  startSerial();
#endif

  DEBUG_PRINT(F("Adafruit Bluefruit Init"));
  DEBUG_PRINT(F("------------------------------------------------"));

  initTCS();
  initPixels();

  /* Initialise the module */
  DEBUG_PRINT(F("Initialising the Bluefruit LE module: "));

  if ( !ble.begin(VERBOSE_MODE) ) {
    error(F("Couldn't find Bluefruit, make sure it's in CoMmanD mode & check wiring?"));
  }
  DEBUG_PRINT_INLINE( F("OK!") );

  tryToDoAFactoryReset();

  DEBUG_PRINT(F("Preflight checks and initization setup"));

  DEBUG_PRINT(F("Calling Begin for TCS Sensor"));
  //Init TCS Sesnor library
  if (tcs.begin()) {
    DEBUG_PRINT(F("Found TCS sensor...we are good to go!"));
  } else {
    error(F("No TCS34725 found...check your connections"));
  }

  DEBUG_PRINT(F("Calling Begin for VCNL Sensor"));
  //Init VCNL Sensor library
  if (vcnl.begin()) {
    DEBUG_PRINT(F("Found VCNL sensor...we are good to go!"));
  } else {
    error(F("No VCNL found...check your connections"));
  }

  //Generate gamma correction table
  initGammaTable();

  /* Disable command echo from Bluefruit */
  ble.echo(false);

  DEBUG_PRINT("Requesting Bluefruit info:");
  /* Print Bluefruit information */
  ble.info();
  ble.verbose(false);  // debug info is a little annoying after this point!

  /* Wait for connection */
  while (! ble.isConnected()) {
      delay(500);
  }

  DEBUG_PRINT(F("***********************"));

  // Set Bluefruit to DATA mode
  DEBUG_PRINT( F("Switching to DATA mode!") );
  ble.setMode(BLUEFRUIT_MODE_DATA);

  DEBUG_PRINT(F("***********************"));

}

void startSerial()
{
  while (!Serial);  // required for Flora & Micro
  delay(500);
  Serial.begin(115200);
}

void initTCS()
{
    //Setup TCS Sensor LED PIN as an output and turn off the LED
  pinMode(TCS_LED_PIN, OUTPUT);
  digitalWrite(TCS_LED_PIN, LOW);
}

void tryToDoAFactoryReset()
{
    if ( FACTORYRESET_ENABLE ) {
      /* Perform a factory reset to make sure everything is in a known state */
      DEBUG_PRINT(F("Performing a factory reset: "));
      if ( ! ble.factoryReset() ) {
        error(F("Couldn't factory reset"));
      }
    }
}

/**************************************************************************/
/*!
    @brief  Constantly poll for new command or response data
*/
/**************************************************************************/
void loop(void)
{
  uint16_t proximity = vcnl.readProximity();
  uint16_t ambient = vcnl.readAmbient();
  if (proximity > PROXIMITY_THRESHOLD) {
    DEBUG_PRINT_INLINE("\t\tProximity = ");
    DEBUG_PRINT(proximity);

    DEBUG_PRINT_INLINE("\t\tAmbient = ");
    DEBUG_PRINT(ambient);

    //Turn on LED and wait a bit for a good reading
    digitalWrite(TCS_LED_PIN, HIGH);
    delay(500);

    uint16_t raw_r, raw_g, raw_b, raw_c;
    tcs.getRawData(&raw_r, &raw_g, &raw_b, &raw_c);
    r = gammatable[GAMMATABLE_INDEX(raw_r, raw_c)];
    g = gammatable[GAMMATABLE_INDEX(raw_g, raw_c)];
    b = gammatable[GAMMATABLE_INDEX(raw_b, raw_c)];

    DEBUG_PRINT_INLINE("R = ");
    DEBUG_PRINT_INLINE(r);
    DEBUG_PRINT_INLINE(" G = ");
    DEBUG_PRINT_INLINE(g);
    DEBUG_PRINT_INLINE(" B = ");
    DEBUG_PRINT(b);

    //Turn off the LED
    digitalWrite(TCS_LED_PIN, LOW);
    //Do we need this pause?
    //The docs say: "pause a bit to prevent constantly reading the color"
    //delay(1000);

    uint32_t c = pixel.Color(r, g, b);
    colorWipe(c, 50);

    delay(1000);

  } else {
    /* Wait for new data to arrive */
    uint8_t len = readPacket(&ble, BLE_READPACKET_TIMEOUT);
    if (len == 0) return;

     /* Got a packet! */
     printHex(packetbuffer, len);

    // Color
    if (packetbuffer[1] == 'C') {
      uint8_t red = packetbuffer[2];
      uint8_t green = packetbuffer[3];
      uint8_t blue = packetbuffer[4];
      DEBUG_PRINT_INLINE(F("RGB #"));
      if (red < 0x10) DEBUG_PRINT_INLINE("0");
      //Serial.print(red, HEX);
      if (green < 0x10) DEBUG_PRINT_INLINE("0");
      //Serial.print(green, HEX);
      if (blue < 0x10) DEBUG_PRINT_INLINE("0");
      //Serial.println(blue, HEX);

      for(uint8_t i=0; i<NUMPIXELS; i++) {
        pixel.setPixelColor(i, pixel.Color(red,green,blue));
      }
      pixel.show(); // This sends the updated pixel color to the hardware.
    }
  }

  //Small Delay before looping???
  //delay(50);
}

void initGammaTable() {
  for  (int i = 0; i < 256; i++) {
    float x = i;
    x /= 255;
    x = pow(x, 2.5);
    x *= 255;
    gammatable[i] = x;
  }
}

void initPixels() {
  pixel.begin(); // This initializes the NeoPixel library.

  //alternate between blue and green colors
  for(uint8_t i=0; i<NUMPIXELS; i+=2) {
    uint32_t green = pixel.Color(0, 255, 0);
    uint32_t blue = pixel.Color(0, 0, 255);
    pixel.setPixelColor(i, green);
    if (i+1 < NUMPIXELS) {
      pixel.setPixelColor(i+1, blue);
    }
  }

  pixel.show();
}

// Fill the dots one after the other with a color
void colorWipe(uint32_t c, uint8_t wait) {
  for(uint16_t i=0; i<pixel.numPixels(); i++) {
      pixel.setPixelColor(i, c);
      pixel.show();
      delay(wait);
  }
}

void animatePixels(uint8_t r, uint8_t g, uint8_t b, int periodMS) {
  int mode = millis()/periodMS % 2;
  for (int i = 0; i < pixel.numPixels(); ++i) {
    if (i%2 == mode) {
      pixel.setPixelColor(i, r, g, b);  // Full bright color.
    } else {
      pixel.setPixelColor(i, r/4, g/4, b/4);  // Quarter intensity color.
    }
  }
  pixel.show();
}
