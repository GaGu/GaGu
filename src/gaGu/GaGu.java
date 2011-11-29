package gaGu;


import com.ibm.saguaro.system.*;


public class GaGu {
    private static long             m_interval;
    private static Timer            m_sampleTimer;
    private static int              m_wanderlustCalib;
    private static int              m_moodCalib;
    private static byte             m_mood;	// 3 = spark not here, 0,1,2 = rd, gn, yl
    private static byte             m_nLEDs;
    private static int              m_gravityThreshold;
    private static byte[]           m_sampleBuffer;
    private static boolean          m_moodWait;

    static GaGu gagu;
	
    static {
        gagu = new GaGu();
        gagu.init();
        gagu.start();
    }
	
    public GaGu() {
    	m_interval = Time.toTickSpan(Time.MILLISECS,200);
    	m_gravityThreshold = 50 / 2;
        m_mood = 0;
        m_sampleTimer = new Timer();
        m_nLEDs = (byte)LED.getNumLEDs();
        m_moodWait = false;
        m_sampleBuffer = new byte[4]; // two for X and two for Y axis
        m_sampleTimer.setCallback(new TimerEvent(this) {
        	public void invoke(byte param, long time) {
        		((GaGu)obj).timerCallback(param,time);
        	}
        });
    }
    
    public void init() {
        for (byte i = 0; i < m_nLEDs; i++)
        	LED.setState( i, (byte)1 );
        calibrateAcc();
        unset_LEDs();
    }

    public void start() {
        m_sampleTimer.setAlarmBySpan(m_interval);
    }

    public void stop() {
        m_sampleTimer.cancelAlarm();
    }

    public void timerCallback(byte param, long time) {
    	m_sampleTimer.setAlarmBySpan(m_interval);
    	getUserInput();
    }
    
    public void getUserInput() {
        int l_wanderlust, l_mood;

        // Read sensor
        SimpleDevices.read(SimpleDevices.MOTE_ACCEL, 0, 0, m_sampleBuffer , 0 , 4);
        l_wanderlust = (int)Util.get16be(m_sampleBuffer,0) - m_wanderlustCalib;
        l_mood       = (int)Util.get16be(m_sampleBuffer,2) - m_moodCalib;
        
        // Change mood?
        if (!m_moodWait) {
        	// Change mood
        	if (l_mood > 0 && l_mood > m_gravityThreshold) {
        		// Prepare LED Status
//        		m_mood = (byte) ((m_mood>=1)? m_mood-1 : m_mood);
        		if (m_mood == 0)
        			m_mood = (byte)(m_nLEDs - 1);
        		else
        			m_mood--;
        		set_LED(m_mood);
        		m_moodWait = true;
        	}
        	else if (l_mood < 0 && l_mood < -m_gravityThreshold) {
        		// Prepare LED status
//        		m_mood = (byte) ((m_mood<m_nLEDs-1)? (m_mood+1) : m_mood);
        		if (m_mood == (byte)(m_nLEDs - 1))
        			m_mood = 0;
        		else
        			m_mood++;
        		set_LED(m_mood);
        		m_moodWait = true;
        	}
        } 
        else {
        	// Mood changed, reset?
        	if ( (l_mood > 0 && l_mood < m_gravityThreshold) || (l_mood < 0 && l_mood > -m_gravityThreshold) ) {
        		// Reset
        		m_moodWait = false;
        	}
        }
        
        
        // Wanderlust?
        if ( (l_wanderlust > 0 && l_wanderlust > m_gravityThreshold) || (l_wanderlust < 0 && l_wanderlust < -m_gravityThreshold) ) {
        	// Send spark away
        	m_mood = m_nLEDs;
        	unset_LEDs();
        } 
    }

    private void calibrateAcc() {

        byte[] l_tempBuffer = new byte[4];

        m_wanderlustCalib         = 0;
        m_moodCalib         = 0;
        int l_nIterations = 8;
        for(int i = 0; i < l_nIterations; i++) {
            // ignore the first values
            try{
                SimpleDevices.read(SimpleDevices.MOTE_ACCEL, 0, 0, l_tempBuffer, 0, 4);
            } catch (MoteException e) {
                continue;
            }
        }
        for(int i = 0; i < l_nIterations; i++) {
            try{
                SimpleDevices.read(SimpleDevices.MOTE_ACCEL, 0, 0, l_tempBuffer, 0, 4);
            } catch (MoteException e) {
                continue;
            }
            m_wanderlustCalib += (int) Util.get16be(l_tempBuffer,0);
            m_moodCalib += (int) Util.get16be(l_tempBuffer,2);
        }

        // take the average
        m_wanderlustCalib >>= 3;
        m_moodCalib >>= 3;
    }

    private void set_LED(byte in_ledNumber) {
    	unset_LEDs();
        LED.setState(in_ledNumber,(byte)1);
    }
    
    private void unset_LEDs() {
        for (byte i = 0; i < m_nLEDs; i++)
            LED.setState(i,(byte)0);
    }
}