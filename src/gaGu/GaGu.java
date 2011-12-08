package gaGu;

//Changes since last synchronization (31.11.2011 - 13:00)
//31.11.2011: 	Use last 3 Bits for Delay ( & 0x07 )
//				Made system faster: 200ms/50ms/5ms
//				Transmission of Synchronizer moved from beaconsent() to listenToFollowerDone()


import com.ibm.saguaro.system.*;


public class GaGu {
    //private static long m_interval;
    //private static Timer m_sampleTimer;
    private static int m_wanderlustCalib;
    private static int m_moodCalib;
    private static byte m_mood; // 3 = spark not here, 0,1,2 = rd, gn, yl
    private static byte m_nLEDs;
    private static int m_gravityThreshold;
    private static byte[] m_sampleBuffer;
    private static boolean m_moodWait;
    
    // sleep for 2 seconds
 	static long SLEEP_INTERVAL_TICKS = Time.toTickSpan(Time.MILLISECS, 2000);
 	// listen interval 100 ms
 	static long LISTEN_INTERVAL_TICKS = Time.toTickSpan(Time.MILLISECS, 100);
 	// delay quanta before answering the beacon
 	static long DELAY = Time.toTickSpan(Time.MILLISECS, 200);
 	// fixed PAN
 	static int PANID = 0x57AC;
	// the extended unique address of a mote
	static byte[] myEUI = new byte[8];
	// the short address of the mote, derived from extended one
	static int myShortAddress;
	// buffer for the synchronization message
	static byte syncMessage[] = new byte[24];
	// time when the next beacon should be received/sent
	static long nextBeacon;
	
	// schedules of neighbors
	static final int MAX_BEACONS = 1;
	static long scheduleTimes[] = new long[MAX_BEACONS];
	static int scheduleAddresses[] = new int[MAX_BEACONS];
	static long scheduleDelays[] = new long[MAX_BEACONS];
	static int beaconCount = 0;
	static int beaconIndex = 0;

    static GaGu gagu;

    static {
        gagu = new GaGu();
        gagu.init();
        gagu.start();
    }

    public GaGu() {
    	//m_interval = Time.toTickSpan(Time.MILLISECS,200);
    	m_gravityThreshold = 50 / 2;
        m_mood = 1;
        //m_sampleTimer = new Timer();
        m_nLEDs = (byte)LED.getNumLEDs();
        m_moodWait = false;
        m_sampleBuffer = new byte[4]; // two for X and two for Y axis
        /*m_sampleTimer.setCallback(new TimerEvent(this) {
         public void invoke(byte param, long time) {
         ((GaGu)obj).timerCallback(param,time);
         }
        });*/
    }
    
    public void init() {
        for (byte i = 0; i < m_nLEDs; i++)
         LED.setState( i, (byte)1 );
        calibrateAcc();
        
        // initialize radio and address
		Radio.acquire();
		Radio.setPanId(PANID, false);
		
		// get short address from extended address
		Radio.getExtAddr(myEUI, 0);
		myShortAddress = Util.get16le(myEUI, 0);
		Radio.setShortAddr(myShortAddress);
        
        unset_LEDs();
    }

    public void start() {
    	//Start timer for sensor values
        //m_sampleTimer.setAlarmBySpan(m_interval);
        
        //Start discovery mode
        // diver if there is already a synchronizer
		Radio.setRxHandler((byte) 0, new RadioRxPdu(null) {
			@Override
			public void invoke(byte[] pdu, int len, long time, int quality) {
				discoverRxPdu(pdu, len, time, quality);
			}
		});

		Radio.setRxDone(new RadioDone(null) {
			@Override
			public void invoke(int info) {
				discoverDone(info);
			}
		});
		
		// Starting discovery mode
		Radio.enableRx(Time.currentTicks() + 2 * SLEEP_INTERVAL_TICKS);
        
    }

    public void stop() {
        //m_sampleTimer.cancelAlarm();
    }

    /*
    public void timerCallback(byte param, long time) {
     m_sampleTimer.setAlarmBySpan(m_interval);
     getUserInput();
    }*/
    
    public static void getUserInput() {
        int l_wanderlust, l_mood;

        // Read sensor
        SimpleDevices.read(SimpleDevices.MOTE_ACCEL, 0, 0, m_sampleBuffer , 0 , 4);
        l_wanderlust = (int)Util.get16be(m_sampleBuffer,0) - m_wanderlustCalib;
        l_mood = (int)Util.get16be(m_sampleBuffer,2) - m_moodCalib;
        
        // Change mood?
        if (!m_moodWait) {
        	// Change mood
        	if (l_mood > 0 && l_mood > m_gravityThreshold) {
        			// Prepare LED Status
        			if (m_mood == 0)
        				m_mood = (byte)(m_nLEDs - 1);
        			else
        				m_mood--;
        			set_LED(m_mood);
        			m_moodWait = true;
        	}
        	else if (l_mood < 0 && l_mood < -m_gravityThreshold) {
        			// Prepare LED status
        			// m_mood = (byte) ((m_mood<m_nLEDs-1)? (m_mood+1) : m_mood);
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
        	syncMessage[7] = m_mood;
        	m_mood = m_nLEDs;
        	unset_LEDs();
         
        }
    }

    private void calibrateAcc() {

        byte[] l_tempBuffer = new byte[4];

        m_wanderlustCalib = 0;
        m_moodCalib = 0;
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

    private static void set_LED(byte in_ledNumber) {
     unset_LEDs();
        LED.setState(in_ledNumber,(byte)1);
    }
    
    private static void unset_LEDs() {
        for (byte i = 0; i < m_nLEDs; i++)
            LED.setState(i,(byte)0);
    }
    
    
    /*****************************Functions for discovery mode*************************************/
	
	/**
	 * We received a beacon during the initial discovery phase. Remember the
	 * address and respective schedule and compute a delay for answering
	 * the beacon.
	 */
	static void discoverRxPdu(byte[] pdu, int len, long time, int quality) {
		
		
		if (beaconCount == MAX_BEACONS)
			return;

		// received a beacon while discovering
		int address = Util.get16le(pdu, 5);
		
		//check whether address already saved in scheduleAddresses[]
		for (int i = 0; i < MAX_BEACONS; i++) {
			if (address == scheduleAddresses[i])
				return; // only unique beacons
		}
		// save schedule for new address
		scheduleAddresses[beaconCount] = address;
		scheduleTimes[beaconCount] = time + SLEEP_INTERVAL_TICKS;
		// use last 3 bits of mote address for "random" answering delay
		scheduleDelays[beaconCount] = (myShortAddress & 0x07) * DELAY + DELAY;
		beaconCount++;
	}

	/**
	 * Initial discovery period completed. Decide whether to start a new
	 * schedule as a synchronizer (with Spark) or become a follower.
	 * 
	 */
	static void discoverDone(int info) {
		
		// header for syncMessage is the same for both, follower and synchronizer
		syncMessage[0] = Radio.FCF_BEACON;
		syncMessage[1] = Radio.FCA_SRC_SADDR;
		syncMessage[2] = 0x01; // sequence number
		Util.set16le(syncMessage, 3, PANID);
		Util.set16le(syncMessage, 5, myShortAddress);

		if (beaconCount != 0) { 
			// we found other beacons - we are follower
			
			// prepare follower
			follower();
			
		}
		
		else {
			// no other beacon while discovering we are a synchronizer (having the Spark)
			
			// prepare synchronizer
			synchronizer();

		}
	}
	
	/******************************Functions & Delegates for Synchronizer***************************/

	/**
	 * Synchronizer
	 */
	static void synchronizer() {
		
		Util.set16le(syncMessage, 5, myShortAddress);
		nextBeacon = Time.currentTicks();
		
		//Switch on LED
		//m_mood = 1;
		set_LED( m_mood );
						
		
		// receive the message from follower
		Radio.setRxHandler((byte) 0, new RadioRxPdu(null) {
			@Override
			public void invoke(byte[] pdu, int len, long time, int quality) {
				listenToFollower(pdu, len, time, quality);
			}
		});
		
		
		Radio.setRxDone(new RadioDone(null) {
			@Override
			public void invoke(int info) {
				listenToFollowerDone(info);
			}
		});
		
		//Start sending syncMessages
		syncMessage[7] = m_nLEDs;
		syncMessage[8] = (byte)0;
		beaconSent(null, 0, 0, 0);
		
		
	}
	
	static void listenToFollower(byte[] pdu, int len, long time, int quality) {
		toggleRedLED();
		//Evaluate Gateway 
		// if pdu[7] == fiktive addresse
		//		modus = pdu[8];
		//else:
		
			//Evaluate Message from Follower
			// if modues == dist
			//		var = quality
			// else
			//		var = 9999 - hell
			
			// if var > best_var
			//		best_var = var;
			//		(best_addr = src_addr)
			//		syncMessage[8] = pdu[7];
		
		
	}
	
	static void listenToFollowerDone(int info) {
		//toggleRedLED();
		
		// Transmission
		Radio.transmit(Radio.TXMODE_CSMA, syncMessage, 0, 11, nextBeacon,
				new RadioTxDone(null) {
					@Override
					public void invoke(byte[] pdu, int len, int status,
							long txend) {
						beaconSent(pdu,len,status,txend);
					}
				});	
		
		getUserInput();
		
		
	}
	
	
	
	/**
	 *  Synchronizer: periodic sending of syncMessage
	 */
	static void beaconSent(byte[] pdu, int len, int status, long txend) {
		
		//toggleRedLED();
		
		//TODO:
		// if syncMessage[7] < 3
		//        start()
		
		Radio.enableRx(Time.currentTicks() + 9 * DELAY);
		
		// Sync Message - periodically sent
		nextBeacon += SLEEP_INTERVAL_TICKS;
		
		// sequence number
		syncMessage[2]++;
		
//		// Transmission
//		Radio.transmit(0, syncMessage, 0, 11, nextBeacon,
//				new RadioTxDone(null) {
//					@Override
//					public void invoke(byte[] pdu, int len, int status,
//							long txend) {
//						beaconSent(pdu,len,status,txend);
//					}
//				});	
		
		
	}
	
	
	
	
	
/*****************************Functions & Delegates for Follower*******************************/
	
	/**
	 *  Follower
	 */
	static void follower() {
		
		// add the address of the synchronizer
		Util.set16le(syncMessage, 7, scheduleAddresses[beaconIndex]);

		// only deal with the first schedule
		beaconIndex = 0;
		scheduleTimes[beaconIndex] += SLEEP_INTERVAL_TICKS;
		nextBeacon = scheduleTimes[beaconIndex];

		// receive the beacon from synchronizer
		Radio.setRxHandler((byte) 0, new RadioRxPdu(null) {
			@Override
			public void invoke(byte[] pdu, int len, long time, int quality) {
				listenToSynchronizer(pdu, len, time, quality);
			}
		});
		
		
		Radio.setRxDone(new RadioDone(null) {
			@Override
			public void invoke(int info) {
				listenToSynchronizerDone(info);
			}
		});

		Radio.enableRx((byte) 0, nextBeacon, nextBeacon+LISTEN_INTERVAL_TICKS);
	}
	

	/**
	 * Follower: received a SYNC message from synchronizer.
	 */
	static void listenToSynchronizer(byte[] pdu, int len, long time, int quality) {
		toggleYellowLED();
		
		//TODO:
		//Spark coming? 
		//if pdu[8] == my address (myShortAddress)
		//		m_mood = syncMessage[7];
		//		set_LED(m_moode);
		//		change to synchronizer mode synchronizer()
		//else
		//	new synchronizer = pdu[8];
		//  
				
	}

	/**
	 * Follower: finished listening.
	 */		
	static void listenToSynchronizerDone(int info) {
		//toggleRedLED();

		//TODO: 
		// Send darkness informations to the synchronizer. 
		
		
		//How to send only to synchronizer?
		
		
		// Transmit the SYNC message to synchronizer with mote info
		Radio.transmit(Radio.TXMODE_CSMA, syncMessage, 0, 11, nextBeacon
				+ scheduleDelays[beaconIndex], new RadioTxDone(null) {
			@Override
			public void invoke(byte[] pdu, int len, int status, long txend) {
				sendmoteinfoDone(pdu, len, status, txend);
			}
		});
		
		/*
		//After transmitting  listen again
		//Move this later in sendmoteinfoDone()
		nextBeacon = scheduleTimes[beaconIndex];
		scheduleTimes[beaconIndex] += SLEEP_INTERVAL_TICKS;

		Radio.enableRx((byte) 0, nextBeacon, nextBeacon+LISTEN_INTERVAL_TICKS);*/
		
	}

	
	
	static void sendmoteinfoDone(byte[] pdu, int len, int status, long txend) {
		toggleRedLED();

		nextBeacon = scheduleTimes[beaconIndex];
		scheduleTimes[beaconIndex] += SLEEP_INTERVAL_TICKS;

		Radio.enableRx((byte) 0, nextBeacon, nextBeacon+LISTEN_INTERVAL_TICKS);
	}

	

/*************************Other Functions used by Follower & Synchronizer***********************/		
	
	static void toggleYellowLED() {
		LED.setState((byte) 0, (byte) (1 ^ LED.getState((byte) 0)));   // ^ = XOR
	}

	static void toggleGreenLED() {
		LED.setState((byte) 1, (byte) (1 ^ LED.getState((byte) 1)));
	}

	static void toggleRedLED() {
		LED.setState((byte) 2, (byte) (1 ^ LED.getState((byte) 2)));
	}
	
/**********************************************************************************************/
    
    
    
    
}