package gaGu;


import com.ibm.saguaro.system.*;


public class Radiotest {

		// sleep for 2 seconds
		static long SLEEP_INTERVAL_TICKS = Time.toTickSpan(Time.SECONDS, 2);
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

		static {
			
			// initialize radio and address
			Radio.acquire();
			Radio.setPanId(PANID, false);
			
			// get short address from extended address
			Radio.getExtAddr(myEUI, 0);
			myShortAddress = Util.get16le(myEUI, 0);
			Radio.setShortAddr(myShortAddress);

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
			
		} //end static
		
		
		
		
/****************************Variables for Synchronizer or Follower mode***********************/

		// schedules of neighbors
		static final int MAX_BEACONS = 1;
		static long scheduleTimes[] = new long[MAX_BEACONS];
		static int scheduleAddresses[] = new int[MAX_BEACONS];
		static long scheduleDelays[] = new long[MAX_BEACONS];
		static int beaconCount = 0;
		static int beaconIndex = 0;

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
			scheduleDelays[beaconCount] = (myShortAddress & 0x03) * DELAY + DELAY;
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
			
			//TODO:
			//Switch on mood LED
			
			//Start sending syncMessages
			beaconSent(null, 0, 0, 0);
			
			//TODO:
			//Set up timer for checking sensor values periodically
			//X-Direction change
			//  Change mood (change LED color)
			//Y-Direction change
			//  Send Spark away to nearest or darkest mote
			//  Start follower()
			
			//TODO:
			//Callback to receive the mote info
			//Evaluate them
			
		}
		
		/**
		 *  Synchronizer: periodic sending of syncMessage
		 */
		static void beaconSent(byte[] pdu, int len, int status, long txend) {
			toggleGreenLED();

			// Sync Message - periodically sent
			nextBeacon += SLEEP_INTERVAL_TICKS;
			
			// sequence number
			syncMessage[2]++;
			
			// Transmission
			Radio.transmit(0, syncMessage, 0, 11, nextBeacon,
					new RadioTxDone(null) {
						@Override
						public void invoke(byte[] pdu, int len, int status,
								long txend) {
							beaconSent(pdu, len, status, txend);
						}
					});			
			
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
					listen(pdu, len, time, quality);
				}
			});
			
			
			Radio.setRxDone(new RadioDone(null) {
				@Override
				public void invoke(int info) {
					listenDone(info);
				}
			});

			Radio.enableRx((byte) 0, nextBeacon, nextBeacon+LISTEN_INTERVAL_TICKS);
		}
		

		/**
		 * Follower: received a SYNC message from synchronizer.
		 */
		static void listen(byte[] pdu, int len, long time, int quality) {
			toggleYellowLED();
			
			//TODO:Decide whether follower received Spark or not ->Start synchronizer()
			
			//TODO:If no Spark received:
			//  Evaluate Quality(Can be used for Distance) and Darkness of Mote
			//  Save the Informations locally and send them after listenDone() 
			//  to the Spark-Mote (Synchronizer)
			
			//TODO:If Spark received:
			//	Set mood (LED on)
			//  Start synchronizer. //What happens with Synchronizer address??
			
		}

		/**
		 * Follower: finished listening.
		 */		
		static void listenDone(int info) {
			toggleRedLED();

			//TODO: 
			// Send the quality and darkness informations to the synchronizer. 
			// Synchronizer has to listen! 
			
			//??? Do we have to rebroadcast the message???
			
			
			/*
			//TODO:
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

} //end public class Radiotest

	

