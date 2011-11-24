package gaGu;

//import
import com.ibm.saguaro.system.*;


public class GaGu {
	
	// Acceleration sensor
	@Immutable
	public static final byte[] acc_cal = {1,1};
	private static byte[] m_sampleBuffer = new byte[4];
	
	// LED status (Spark Mood)
	private static byte onLED; // 3 = spark not here, 0,1,2 = rd, gn, yl
	
	// Timer
	private static Timer timer = new Timer();
	//private static long INTERVAL = Time.toTickSpan(Time.MILLISECS, 200);
	private static long INTERVAL = Time.toTickSpan(Time.SECONDS, 1);

		
	// Main
	static {
		
		// Calibrate Sensors
		LED.setState( (byte)0, (byte)1 );
		LED.setState( (byte)1, (byte)1 );
		LED.setState( (byte)2, (byte)1 );
		// Calibrate Acceleration
		calAcc();
		// Calibrate Light
		//calLight();
		LED.setState( (byte)0, (byte)0 );
		LED.setState( (byte)1, (byte)0 );
		LED.setState( (byte)2, (byte)0 );
		
		timer.setCallback(new TimerEvent(null) {
			public void invoke(byte param, long time) {
				GaGu.onTimeout(param, time);
			}
		});
		
		timer.setAlarmBySpan(INTERVAL);
		
		
		
	}
	
	
	

	// Calibrate Acceleration Sensor
	static void calAcc() {
	    byte[] l_tempBuffer = new byte[4];
	    int m_xCalib = 0; 
	    int m_yCalib = 0;
	    int l_nIterations = 8;
	    

	    for (int i = 0; i < l_nIterations; i++) {
	        try{
	            SimpleDevices.read(SimpleDevices.MOTE_ACCEL, 0, 0, l_tempBuffer, 0, 4);
	        } catch (MoteException e) {
	            continue; 
	        }
	        m_xCalib += (int) Util.get16be(l_tempBuffer,0); 
	        m_yCalib += (int) Util.get16be(l_tempBuffer,2); 
	    }

	    m_xCalib >>= 3;
	    m_yCalib >>= 3;
	    
	    l_tempBuffer[0] = (byte)m_xCalib;
	    l_tempBuffer[1] = (byte)m_yCalib;
	    l_tempBuffer[2] = (byte)0;
	    l_tempBuffer[3] = (byte)0;
	    
	    Util.updatePersistentData(l_tempBuffer, 0, acc_cal, 0, acc_cal.length);
	}
	
	// Calibrate Light Sensor
	static void calLight() {
		
	}
	
	// Spark mood and wanderlust
	static void onTimeout(byte param, long time) {
		rotLed(true);
		//updateAcc();
		//changeMood();
		//changeHome();
		timer.setAlarmBySpan(INTERVAL);
	}
	
	// Update acceleration sensor values
	static void updateAcc() {
		SimpleDevices.read(SimpleDevices.MOTE_ACCEL, 0, 0, m_sampleBuffer , 0 , 4);
	}
	
	// Run updateAcc first!
	static void changeMood() {
		int moodDir = (int)Util.get16be(m_sampleBuffer,0);
		if (moodDir > 1)		// TODO: adjust threshold
			rotLed(true);
		else if (moodDir < -1)	// TODO: adjust threshold
			rotLed(false);
	}
	
	// Run updateAcc first!
	static void changeHome() {
		int wanderlust = (int)Util.get16be(m_sampleBuffer,2);
		if (wanderlust > 1 | wanderlust < -1) {
			// TODO: send spark away
			allLedsOff();
		}
	}
	
	// Rotate LED
	// Rotate LED left if switchLeft = true else rotate LED right
	static void rotLed(boolean switchLeft) {
		// Prepare LED Status
		if (switchLeft) {
			if (onLED == 2)
				onLED = 0;
			else
				onLED++;
		} else {
			if (onLED == 0)
				onLED = 2;
			else
				onLED--;
		}
		
		// Update LED Status
		for (byte i = 0; i < 3; i++) {
			if (onLED == i)
				LED.setState(i, (byte)1);
			else
				LED.setState(i, (byte)0);
		}
	}
	
	// Set LED off

    static void allLedsOff () {
        // turn-off all LEDs
        for (byte i = 0; i < 3; i++)
            LED.setState(i, (byte)0);
    }
	

}
