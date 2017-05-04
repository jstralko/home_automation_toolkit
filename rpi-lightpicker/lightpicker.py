import sys

LED_STRIP_LEN 	= 160

#Load driver for your hardware, visualizer just for example
from bibliopixel.drivers.LPD8806 import DriverLPD8806
driver = DriverLPD8806(num = LED_STRIP_LEN)

#load the LEDStrip class
from bibliopixel.led import *
led = LEDStrip(driver)

#load some cool shit right her'
from strip_animations import *
#anim = Rainbow(led)

from bibliopixel.colors import *
mycolors = [colors.Red, colors.Orange, colors.Yellow, colors.Green, colors.PapayaWhip, colors.Blue, colors.Purple, colors.Pink, colors.Honeydew, colors.Chocolate,
		colors.NavajoWhite, colors.Olive, colors.DarkSalmon, colors.IndianRed, colors.Navy, colors.SeaGreen]

# Import Adafruit IO MQTT client.
from Adafruit_IO import MQTTClient

# Set to your Adafruit IO key & username below.
ADAFRUIT_IO_KEY      = '8039edb08a0fe574097a021c0d0b4d9f8ba48f41'
ADAFRUIT_IO_USERNAME = 'gerbstralko'

# Define callback functions which will be called when certain events happen.
def connected(client):
    # Connected function will be called when the client is connected to Adafruit IO.
    # This is a good place to subscribe to feed changes.  The client parameter
    # passed to this function is the Adafruit IO MQTT client so you can make
    # calls against it easily.
    print 'Connected to Adafruit IO!  Listening for PI changes...'
    # Subscribe to changes on a feed named pi.
    client.subscribe('pi')

def disconnected(client):
    # Disconnected function will be called when the client disconnects.
    print 'Disconnected from Adafruit IO!'
    sys.exit(1)

def message(client, feed_id, payload):
    # Message function will be called when a subscribed feed has a new value.
    # The feed_id parameter identifies the feed, and the payload parameter has
    # the new value.
    print 'Feed {0} received new value: {1}'.format(feed_id, payload)
	led.fill(colors.Purple, 0, LED_STRIP_LEN)
	led.update()


# Create an MQTT client instance.
client = MQTTClient(ADAFRUIT_IO_USERNAME, ADAFRUIT_IO_KEY)

# Setup the callback functions defined above.
client.on_connect    = connected
client.on_disconnect = disconnected
client.on_message    = message

# Connect to the Adafruit IO server.
client.connect()
client.loop_blocking()
