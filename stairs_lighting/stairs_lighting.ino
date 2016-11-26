/* 
 *  Stairs projet
 */
#include <Adafruit_NeoPixel.h>

#define NEOPIXEL_PIN 11
#define LEDPIN 13
#define SENSORPIN 4

Adafruit_NeoPixel strip = Adafruit_NeoPixel(180, NEOPIXEL_PIN, NEO_GRB + NEO_KHZ800);

// variables will change:
int sensorState = 0, lastState=0;         // variable for reading the pushbutton status

void setup() {
  strip.begin();
  strip.show(); // Initialize all pixels to 'off'
  // initialize the LED pin as an output:
  pinMode(LEDPIN, OUTPUT);      
  // initialize the sensor pin as an input:
  pinMode(SENSORPIN, INPUT);     
  digitalWrite(SENSORPIN, HIGH); // turn on the pullup
  
  Serial.begin(115200);

  Serial.println("started");
}

void loop(){
  // read the state of the pushbutton value:
  sensorState = digitalRead(SENSORPIN);

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
  
  if (sensorState && !lastState) {
    Serial.println("Unbroken");
    colorWipe(strip.Color(0, 0, 0), 0); // off??
    
  } 
  if (!sensorState && lastState) {
    Serial.println("Broken");
    colorWipe(strip.Color(255, 0, 0), 0); // Red
  }
  lastState = sensorState;
}

// Fill the dots one after the other with a color
void colorWipe(uint32_t c, uint8_t wait) {
  for(uint16_t i=0; i<strip.numPixels(); i++) {
      strip.setPixelColor(i, c);
      strip.show();
      delay(wait);
  }
}

