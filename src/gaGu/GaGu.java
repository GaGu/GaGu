package gaGu;


import com.ibm.saguaro.system.*;


public class GaGu {
	
//	@Immutable
//	public static final byte[] accCal = {1,1};
    private static int m_xCalib = 0; 
    private static int m_yCalib = 0;
	private static byte[] accVal = new byte[4];
	private static int sparkMood;
	private static int sparkWanderlust;
    
	// LED status (Spark Mood)
	private static byte spark; // 3 = spark not here, 0,1,2 = rd, gn, yl

	
	// Timer
	//private static final long INTERVAL = Time.toTickSpan(Time.SECONDS, 1);
	private static final long INTERVAL = Time.toTickSpan(Time.MILLISECS, 200);
	private static Timer timer = new Timer();
	

	// Main
	static {
		spark = (byte)(Util.rand8() & 3);	// TODO debug acc sensor 
		
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
	    
	    
	    
//	    l_tempBuffer[0] = (byte)m_xCalib;
//	    l_tempBuffer[1] = (byte)m_yCalib;
//	    l_tempBuffer[2] = (byte)0;
//	    l_tempBuffer[3] = (byte)0;
//	    
//	    Util.updatePersistentData(l_tempBuffer, 0, accCal, 0, accCal.length);
	}
	
	// Calibrate Light Sensor
	static void calLight() {
		
	}
	
	// Spark mood and wanderlust
	static void onTimeout(byte param, long time) {
		updateAcc();
		changeMood();
		//changeHome();
		timer.setAlarmBySpan(INTERVAL);
	}
	
	// Update acceleration sensor values
	static void updateAcc() {
		// Read sensor
		SimpleDevices.read(SimpleDevices.MOTE_ACCEL, 0, 0, accVal , 0 , 4);
		// Store calibrated sensor values
		sparkMood = (int)Util.get16be(accVal,0) - m_xCalib;
//	    sparkMood = (int)Util.get16be(accVal,0);
//	    sparkMood -= (int)Util.get16be(accCal, 0);
		sparkWanderlust = (int)Util.get16be(accVal,2) - m_yCalib;
//	    sparkWanderlust = (int)Util.get16be(accVal,2);
//	    sparkWanderlust -= (int)Util.get16be(accCal, 2);
	}
	
	// Run updateAcc first!
	static void changeMood() {
		if (sparkMood > 0)			// TODO: adjust threshold
			rotLed(true);
		else if (sparkMood < 0)	// TODO: adjust threshold
			rotLed(false);
	}
	
	// Run updateAcc first!
	static void changeHome() {
		int wanderlust = (int)Util.get16be(accVal,2);
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
			if (spark == 2)
				spark = 0;
			else
				spark++;
		} else {
			if (spark == 0)
				spark = 2;
			else
				spark--;
		}
		
		// Update LED Status
		for (byte i = 0; i < 3; i++) {
			if (spark == i)
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
