/*
 * Read light sensor and set highest three bit to LEDs
 * 
 * Hardware component: Taos TSL2550
 * Length: 2 bytes
 * Range: [0, 4095]
 * Read: SimpleDevices.read(SimpleDevices.MOTE_LIGHT, 0, 0, data, 0, 2);
 * 
 * [0-1] 2 bytes unsigned big-endian for the light value on channel 0
 * The raw ADC value is computed for channel 0 using the formula on page 9 in the datasheet.
 * Note: sampling takes 400 ms
 * 
 */


package gaGu;

import com.ibm.saguaro.system.*;

public class Lighttest {
	static byte m_sunglass = 9; // The bigger the darker the sunglass; range:  [0,13]
	static byte[] m_sampleBuffer = new byte[2];
	static final long c_interval = Time.toTickSpan(Time.MILLISECS, 200); // Shorter thab sampling time!?!
	static final byte m_nLEDs = (byte)LED.getNumLEDs();
	static Timer timer = new Timer();
	
	static Lighttest lt;
	
	static {
		lt = new Lighttest();
		
		timer.setCallback(new TimerEvent(null) {
				public void invoke(byte param, long time) {
				    onTimeout(param, time);
				}
			});
		timer.setAlarmBySpan(c_interval);

	}
	
	static void onTimeout(byte param, long time) {
		// Schedule next sample
		timer.setAlarmBySpan(c_interval);
		
		// Sampling takes 400 ms
		SimpleDevices.read(SimpleDevices.MOTE_LIGHT, 0, 0, m_sampleBuffer, 0, 2);
		int l_val = (int)Util.get16be(m_sampleBuffer,0);
		l_val >>= m_sunglass;
		setLED( (byte)(l_val) );
	}
	
    static void setLED(byte in_ledNumber) {
    	for (byte i = 0; i < m_nLEDs; i++) {
    		LED.setState( i, (byte)(in_ledNumber & (byte)0x01) );
    		in_ledNumber >>= 1;
    	}
    }
}