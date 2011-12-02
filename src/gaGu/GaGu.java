package gaGu;


import com.ibm.saguaro.system.*;


/* TODO:
- Read light sensor
- Send to specific dest address
- Change communication to CSMA
- Reduce intervals and delays
- Change modus from gateway
*/


public class GaGu {
    private static int              m_wanderlustCalib;
    private static int              m_moodCalib;
    private static byte             m_mood;	// 3 = spark not here, 0,1,2 = rd, gn, yl
    private static byte             m_nLEDs;
    private static int              m_gravityThreshold;
    private static byte[]           m_sampleBuffer;
    private static boolean          m_moodWait;
    
	// sleep for 2 seconds
	static long SLEEP_INTERVAL_TICKS = Time.toTickSpan(Time.MILLISECS, 200);
	// listen interval 100 ms
	static long LISTEN_INTERVAL_TICKS = Time.toTickSpan(Time.MILLISECS, 50);
	// delay quanta before answering the beacon
	static long DELAY = Time.toTickSpan(Time.MILLISECS, 5);
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
	
    /****************************Variables for Synchronizer or Follower mode***********************/
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
    	m_gravityThreshold = 50 / 2;
        m_mood = 1;
        m_nLEDs = (byte)LED.getNumLEDs();
        m_moodWait = false;
        m_sampleBuffer = new byte[4]; // two for X and two for Y axis
    }
    
    public void init() {
    	// All LEDs on
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
		
		// All LEDs off
        unset_LEDs();
    }

    public void start() {   
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
    
    public static void getUserInput() {
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
        		if (m_mood == 0)
        			m_mood = (byte)(m_nLEDs - 1);
        		else
        			m_mood--;
        		set_LED(m_mood);
        		m_moodWait = true;
        	}
        	else if (l_mood < 0 && l_mood < -m_gravityThreshold) {
        		// Prepare LED status
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
        	// Prepare spark away
        	syncMessage[7] = m_mood;
        	m_mood = m_nLEDs;
        	unset_LEDs();
        	// Become follower
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
		// use last 2 bits of mote address for "random" answering delay
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
		
		//Switch on mood LED
		set_LED(m_mood);
		
		//Start sending syncMessages
		syncMessage[7] = m_nLEDs;
		syncMessage[8] = 0;
		beaconSent(null, 0, 0, 0);
		
		// Listen to follower to send answer
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
	}
	
	/**
	 *  Synchronizer: periodic sending of syncMessage
	 */
	static void beaconSent(byte[] pdu, int len, int status, long txend) {
		// TODO change to follower mode?
		//if syncMessage[7] < 3
			// then start() (change to follower mode)
		
		Radio.enableRx(Time.currentTicks() + 8 * DELAY);

		// Sync Message - periodically sent
		nextBeacon += SLEEP_INTERVAL_TICKS;
		
		// sequence number
		syncMessage[2]++;
		
	}
	
	/**
	 * Synchronizer: received a SYNC message from follower.
	 */
	static void listenToFollower(byte[] pdu, int len, long time, int quality) {
		// TODO: Check gateway message
		
		// TODO: Check if next home is different
		// if modus == dist
			//var=qual
		// else
			//var=9999 - hell
		
		// if var > best_var
			//best_var = var
			//best_addr = src_addr
//			syncMessage[8] = pdu[7];
		
	}

	/**
	 * Synchronizer: finished listening.
	 */		
	static void listenToFollowerDone(int info) {
		// Transmission
		Radio.transmit(0, syncMessage, 0, 11, nextBeacon,
				new RadioTxDone(null) {
					@Override
					public void invoke(byte[] pdu, int len, int status,
							long txend) {
						beaconSent(pdu, len, status, txend);
					}
				});
		
		getUserInput();
	}
	
	
	
	/*****************************Functions & Delegates for Follower*******************************/
	
	/**
	 *  Follower
	 */
	static void follower() {
		
		// add the address of the synchronizer
//		Util.set16le(syncMessage, 7, scheduleAddresses[beaconIndex]);

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
		// TODO: Spark coming?
		// if pdu[8] == myShortAddress (if new_spark_home == my_address)
//			m_mood == syncMessage[7];
//			set_LED(m_mood)
//			change to synchronizer mode
		// else
//			new_synchronizer = pdu[8]
		
	}

	/**
	 * Follower: finished listening.
	 */		
	static void listenToSynchronizerDone(int info) {

		//TODO: After delay(address) send darkness information to the synchronizer. 
		// How to send only to synchronizer?
		/*
		// Transmit the SYNC message to synchronizer with mote info
		Radio.transmit(0, syncMessage, 0, 11, nextBeacon
				+ scheduleDelays[beaconIndex], new RadioTxDone(null) {
			@Override
			public void invoke(byte[] pdu, int len, int status, long txend) {
				sendmoteinfoDone(pdu, len, status, txend);
			}
		});*/
		
		//After transmitting  listen again
		//Move this later in sendmoteinfoDone()
		nextBeacon = scheduleTimes[beaconIndex];
		scheduleTimes[beaconIndex] += SLEEP_INTERVAL_TICKS;

		Radio.enableRx((byte) 0, nextBeacon, nextBeacon+LISTEN_INTERVAL_TICKS);
		
	}

	/*
	//TODO:
	static void sendmoteinfoDone(byte[] pdu, int len, int status, long txend) {
		toggleRedLED();

		nextBeacon = scheduleTimes[beaconIndex];
		scheduleTimes[beaconIndex] += SLEEP_INTERVAL_TICKS;

		Radio.enableRx((byte) 0, nextBeacon, nextBeacon+LISTEN_INTERVAL_TICKS);
	}*/
}