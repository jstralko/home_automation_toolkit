/* 
 *  Stairs projet
 */
#include <Adafruit_NeoPixel.h>

#define NEOPIXEL_PIN 11
#define LEDPIN 13

#define STAIR_1_PIN 4
#define STAIR_2_PIN 5
#define STAIR_3_PIN 6
#define STAIR_4_PIN 7
#define STAIR_5_PIN 8

#define WALKING_UP    1
#define WALKING_DOWN  2
#define WALKING_TIMEOUT 2000 

#define DEBUG
#define DEBUG_STAIR_PIN STAIR_5_PIN

struct walking_state {
  int firstStep;
  long firstStepTime;
  int walkingDirection;
};
struct walking_state state;

struct stair {
  int pin;
  int sensorState;
  int lastState;
  uint32_t color;
  int active;
};

#define NUM_OF_STAIRS 5
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
  initStair(2, STAIR_3_PIN, strip.Color(0, 0, 255));    //blue
  initStair(3, STAIR_4_PIN, strip.Color(255, 100, 0));  //orange
  initStair(4, STAIR_5_PIN, strip.Color(75, 0, 255));  //purple
   
  // initialize the LED pin as an output:
  pinMode(LEDPIN, OUTPUT);

  clearWalkingState();
  
  Serial.begin(115200);
}

void loop(){

  if (state.firstStep != -1) {
    long elapsed =  millis() - state.firstStepTime;
    //Serial.println(elapsed);
    if (elapsed > WALKING_TIMEOUT) {
     clearWalkingState();
    }
  }

  for (int i = 0; i < NUM_OF_STAIRS; i++) {

    int sensorState = stairs[i].sensorState;
    int lastState = stairs[i].lastState;
    
    sensorState = digitalRead(stairs[i].pin);

#ifdef DEBUG
    toggleDebugLED(sensorState, &stairs[i]);
#endif
    
    if (sensorState && !lastState) {
      Serial.print("Unbroken for ");
      Serial.println(i);
      colorWipe(strip.Color(0, 0, 0), 0, state.walkingDirection); // off
      
    } 
    if (!sensorState && lastState) {
      Serial.print("Broken for ");
      Serial.println(i);
      
      if (state.firstStep == -1) {
        state.firstStep = i;
        state.walkingDirection = i < 2 ? WALKING_UP : WALKING_DOWN; 
      }
      
      if (i == 4) {
        rainbow(0);
      } else {
        colorWipe(stairs[i].color, 0, state.walkingDirection);
      }
    }

    if (state.firstStep != -1 && !sensorState) {
      state.firstStepTime = millis();
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

void clearWalkingState() {
    state.firstStep = -1;
    state.walkingDirection = WALKING_UP;
}

void disableStair(int index)
{
  if (index > 0 && index < NUM_OF_STAIRS) {
    stairs[index].active = 0;
  }
}

void initStair(int index, int pin, uint32_t color) {
  stairs[index].pin = pin;
  stairs[index].sensorState = 0;
  stairs[index].lastState = 0;
  stairs[index].color = color;
  stairs[index].active = 1;
    // initialize the sensor pin as an input:
  pinMode(stairs[index].pin, INPUT);     
  digitalWrite(stairs[index].pin, HIGH); // turn on the pullup
}

void rainbow(uint8_t wait) {
  uint16_t i, j;

  for(j=0; j<256; j++) {
    for(i=0; i<strip.numPixels(); i++) {
      strip.setPixelColor(i, Wheel((i+j) & 255));
    }
    strip.show();
    delay(wait);
  }
}


// Input a value 0 to 255 to get a color value.
// The colours are a transition r - g - b - back to r.
uint32_t Wheel(byte WheelPos) {
  WheelPos = 255 - WheelPos;
  if(WheelPos < 85) {
   return strip.Color(255 - WheelPos * 3, 0, WheelPos * 3);
  } else if(WheelPos < 170) {
    WheelPos -= 85;
   return strip.Color(0, WheelPos * 3, 255 - WheelPos * 3);
  } else {
   WheelPos -= 170;
   return strip.Color(WheelPos * 3, 255 - WheelPos * 3, 0);
  }
}

// Fill the dots one after the other with a color
void colorWipe(uint32_t c, uint8_t wait, int direct) {
  if (direct == WALKING_DOWN) {
      /*
       * Okay, learned something today.
       * You need to make this signed and not unsigned.
       * Or the loop will cause the arduino to crash.
       * Offical documentation from Adafruit:
       * 
       * Counting down to 0 with an unsigned integer (uint16_t) 
       * causes an issue. (An unsigned integer will always be >=0, 
       * thus causing an infinite loop.)
       */
      for(int16_t i = (strip.numPixels() - 1); i >= 0; i--) {
        strip.setPixelColor(i, c);
        strip.show();
        delay(wait);
      }
  } else {
    for(uint16_t i=0; i<strip.numPixels(); i++) {
        strip.setPixelColor(i, c);
        strip.show();
        delay(wait);
    }
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

