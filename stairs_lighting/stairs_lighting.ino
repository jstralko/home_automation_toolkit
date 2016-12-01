/* 
 *  Stairs projet
 */
#include <Adafruit_NeoPixel.h>

#define NEOPIXEL_PIN 11
#define LEDPIN 13

#define STAIR_1_PIN 4
#define STAIR_2_PIN 5
#define STAIR_3_PIN 6

#define DEBUG
#define DEBUG_STAIR_PIN STAIR_2_PIN

struct stair {
  int pin;
  int sensorState;
  int lastState;
  uint32_t color;
};

#define NUM_OF_STAIRS 2
struct stair stairs[NUM_OF_STAIRS];

// IMPORTANT: To reduce NeoPixel burnout risk, add 1000 uF capacitor across
// pixel power leads, add 300 - 500 Ohm resistor on first pixel's data input
// and minimize distance between Arduino and first pixel.  Avoid connecting
// on a live circuit...if you must, connect GND first.
Adafruit_NeoPixel strip = Adafruit_NeoPixel(180, NEOPIXEL_PIN, NEO_GRB + NEO_KHZ800);

void setup() {  
  strip.begin();
  strip.show(); // Initialize all pixels to 'off'

  initStair(0, STAIR_1_PIN, strip.Color(255, 0, 0));    //red
  initStair(1, STAIR_2_PIN, strip.Color(0, 255, 0));    //green
  //initStair(2, STAIR_3_PIN, strip.Color(0, 0, 255));  //blue
  
  // initialize the LED pin as an output:
  pinMode(LEDPIN, OUTPUT);
    
  Serial.begin(115200);
}

void loop(){

  for (int i = 0; i < NUM_OF_STAIRS; i++) {

    int sensorState = stairs[i].sensorState;
    int lastState = stairs[i].lastState;
    
    // read the state of the pushbutton value:
    sensorState = digitalRead(stairs[i].pin);

#ifdef DEBUG
    toggleDebugLED(sensorState, &stairs[i]);
#endif
    
    if (sensorState && !lastState) {
      Serial.print("Unbroken for ");
      Serial.println(i);
      colorWipe(strip.Color(0, 0, 0), 0); // off
      
    } 
    if (!sensorState && lastState) {
      Serial.print("Broken for ");
      Serial.println(i);
      colorWipe(stairs[i].color, 0);
    }
    lastState = sensorState;
    stairs[i].lastState = lastState;
  }
}

void toggleDebugLED(int sensorState, struct stair *stair) {
      if (DEBUG_STAIR_PIN == stair->pin) {
      // check if the sensor beam is broken
      // if it is, the sensorState is LOW:
      if (sensorState == LOW) {     
        // turn LED on:
        digitalWrite(LEDPIN, HIGH);
      } 
      else {
        // turn LED off:
        digitalWrite(LEDPIN, LOW); 
      }
    }
}

void initStair(int index, int pin, uint32_t color) {
  stairs[index].pin = pin;
  stairs[index].sensorState = 0;
  stairs[index].lastState = 0;
  stairs[index].color = color;
    // initialize the sensor pin as an input:
  pinMode(stairs[index].pin, INPUT);     
  digitalWrite(stairs[index].pin, HIGH); // turn on the pullup
} 

// Fill the dots one after the other with a color
void colorWipe(uint32_t c, uint8_t wait) {
  for(uint16_t i=0; i<strip.numPixels(); i++) {
      strip.setPixelColor(i, c);
      strip.show();
      delay(wait);
  }
}

//Theatre-style crawling lights.
void theaterChase(uint32_t c, uint8_t wait) {
  for (int j=0; j<10; j++) {  //do 10 cycles of chasing
    for (int q=0; q < 3; q++) {
      for (int i=0; i < strip.numPixels(); i=i+3) {
        strip.setPixelColor(i+q, c);    //turn every third pixel on
      }
      strip.show();
     
      delay(wait);
     
      for (int i=0; i < strip.numPixels(); i=i+3) {
        strip.setPixelColor(i+q, 0);        //turn every third pixel off
      }
    }
  }
}

