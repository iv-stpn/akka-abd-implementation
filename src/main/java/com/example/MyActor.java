package com.example;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.awt.Desktop;
import java.io.File;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MyActor extends UntypedAbstractActor {
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this); // Logger attached to the actor
	private final PrintWriter output;
	private final PrintWriter output_perfs;
	private final ActorRef self;

	private final int id;
	private final int N;
	private final int f;

	private long start;
	
	private Members processes;
	private ActorRef main;

	private int curr_operation = 0;
	private int state = 0;

	private Map<Integer, Integer> register = new HashMap<Integer, Integer>();
	
	private Map<ActorRef, Long> processes_times = new HashMap<ActorRef, Long>();
	
	private int timestamp = 0;
	
	private int getMethod;
	
	private ArrayList<WriteAckMsg> curr_ack_put = new ArrayList<WriteAckMsg>();
	private ArrayList<ReadAckMsg> curr_ack_get = new ArrayList<ReadAckMsg>();
	private ArrayList<WriteFinishedMsg> curr_ack_write = new ArrayList<WriteFinishedMsg>();
	
	private int curr_ack_void = 0;
	
	private ActorSystem system;
	
	public MyActor(int _id, int _N, int _f, int _getMethod, PrintWriter _output, PrintWriter _output_perfs) {
		id = _id;
		N = _N;
		f = _f;
		self = this.context().self();
		output = _output;
		output_perfs = _output_perfs;
		start = System.currentTimeMillis();
	}

	public String toString() {
		return "Process{" + "id=" + id + "}";
	}

	/**
	 * Static function creating actor
	 */
	public static Props createActor(int id, int N, int f, int getMethod, PrintWriter output, PrintWriter output_perfs) {
		return Props.create(MyActor.class, () -> {
			return new MyActor(id, N, f, getMethod, output, output_perfs);
		});
	}

	public void setFaulty(FaultyMsg msg) {
		state = -1;
		main = msg.main;
	}

	public void log_info(String string) {
		log.info(self.path().name() + ": " + string);
	}

	public void putReceived(PutMsg msg, ActorRef sender) {
		curr_operation++;
		log_info("Starting put operation (curr_iter: " + msg.currIter + ", curr_op: " + curr_operation + ", until: " + msg.M + ").");
		state = 1;

		log_info("Sending write acknowledgement requests.");
		for (ActorRef ref : processes.references.keySet()) {
			ref.tell(new WriteAckReqMsg(msg.k, msg.v, curr_operation, msg.launchGet, msg.currIter, msg.M), self);
		}
	}

	private void writeAckReqReceived(WriteAckReqMsg msg, ActorRef sender) {
		WriteAckMsg m = new WriteAckMsg(msg.k, msg.v, register.get(msg.k), timestamp, msg.currOperation, msg.launchGet,
				msg.currIter, msg.M);
		log_info("Acknowledge/write request " + msg + " received from " + sender.path().name() + ". Sending " + m
				+ " to " + sender.path().name() + ".");
		sender.tell(m, self);
	}
	
	public void confirmPut(WriteAckMsg msg, ActorRef sender) {
		curr_ack_put.add(msg);
		log_info("Acknowledge/put answer received from " + sender.path().name() + ". Received " + msg);

		if (curr_ack_put.size() >= N-f) {
			log_info("PUT : Got answers from a majority of actors. Continuing put operation <" + msg.k + ", "
					+ msg.v + ", " + (timestamp + 1) + ">.");
			WriteAckMsg max_ack = Collections.max(curr_ack_put,
					(a, b) -> ((Integer) a.timestamp).compareTo(b.timestamp));

			boolean same_value_for_max_timestamp = true;
			for (WriteAckMsg ack : curr_ack_put) {
				if (ack.timestamp == max_ack.timestamp && ack.my_v != max_ack.my_v) {
					same_value_for_max_timestamp = false;
					break;
				}
			}

			if (same_value_for_max_timestamp) {
				log_info("Got coherent maximum timestamp. Continuing put operation <" + msg.k + ", "
						+ msg.v + ", " + (timestamp + 1) + ">.");				
				
				state = 3;
				for (ActorRef ref : processes.references.keySet()) {
					ref.tell(new WriteMsg(msg.k, msg.v, timestamp + 1, false, curr_operation, msg.launchGet,
							msg.currIter, msg.M), self);
				}

			} else {
				log_info("Got incoherent timestamp. Cancelling put operation.");
			}

			curr_ack_put.clear();
		}
	}

	public void getReceived(GetMsg msg, ActorRef sender) {
		curr_operation++;
		log_info("Starting get operation (curr_iter: " + msg.currIter + ", curr_op: " + curr_operation + ", until: " + msg.M + ").");
		state = 2;

		log_info("Sending read acknowledgement requests.");
		for (ActorRef ref : processes.references.keySet()) {
			ref.tell(new ReadAckReqMsg(msg.k, curr_operation, msg.currIter, msg.M), self);
		}
	}
	
	private void readAckReqReceived(ReadAckReqMsg msg, ActorRef sender) {
		ReadAckMsg m = new ReadAckMsg(msg.k, register.get(msg.k), timestamp, msg.currRead, msg.currIter, msg.M);
		log_info("Acknowledge/get request received from " + sender.path().name() + " for key " + msg.k + ". Sending "
				+ m + " to " + sender.path().name() + ".");
		sender.tell(m, self);
	}
	
	public void confirmGet(ReadAckMsg msg, ActorRef sender) {
		log_info("Acknowledge/get answer " + msg + " received from " + sender.path().name() + ".");
		curr_ack_get.add(msg);

		if (curr_ack_get.size() >= N-f) {
			log_info("GET: Got answers from a majority of actors. Quorum achieved, continuing.");
			ReadAckMsg max_ack = Collections.max(curr_ack_get,
					(a, b) -> ((Integer) a.timestamp).compareTo(b.timestamp));

			boolean same_value_for_max_timestamp = true;
			for (ReadAckMsg ack : curr_ack_get) {
				if (ack.timestamp == max_ack.timestamp && ack.v != max_ack.v) {
					same_value_for_max_timestamp = false;
					break;
				}
			}

			if (same_value_for_max_timestamp) {
				log_info("Got coherent maximum timestamp. Continuing get operation.");
				
				state = 3;
				for (ActorRef ref : processes.references.keySet()) {
					ref.tell(new WriteMsg(msg.k, msg.v, timestamp, true, curr_operation, true, msg.currIter, msg.M), self);
				}
				
				log_info("Get operation complete. Returning <k, v, t : (" + msg.k + ", " + max_ack.v + ", "
						+ max_ack.timestamp + ")>.");
			} else {
				log_info("Got incoherent timestamp. Cancelling get operation.");
			}
			
			curr_ack_get.clear();

		}
	}

	private void writeReceived(WriteMsg msg, ActorRef sender) {
		log_info("Write operation " + msg + " received from " + sender.path().name() + ".");
		if (msg.timestamp > timestamp || (msg.timestamp == timestamp && msg.v < register.get(msg.k))) {
			timestamp = msg.timestamp;
			register.put(msg.k, msg.v);
		} else {
			log_info("Write operation non-effective because of lower timestamp/higher value.");
		}
		WriteFinishedMsg m = new WriteFinishedMsg(msg.k, register.get(msg.k), msg.timestamp, msg.readOrigin,
				msg.currOperation, msg.launchGet, msg.currIter, msg.M);
		log_info("Write operation complete with " + msg + ", sending " + m + " to " + sender.path().name() + ".");
		sender.tell(m, self);		
		
	}
	
	private void writeFinishedReceived(WriteFinishedMsg msg, ActorRef sender) {
		log_info("Acknowledge/writeFinished answer " + msg + " received from " + sender.path().name() + ".");
		curr_ack_write.add(msg);

		if (curr_ack_write.size() >= N / 2) {
			state = 0;
			
			log_info("WRITE FINISHED : Got answers from a majority of actors. Quorum achieved, continuing.");
			curr_ack_write.clear();

			if (msg.readOrigin) {
				log_info("Get operation complete. Returning <k, v, t : (" + msg.k + ", " + msg.v + ", "
						+ msg.timestamp + ")>.");
			} else {
				log_info("Put operation complete with (k = " + msg.k + ", v = " + msg.v + "); new timestamp: " + msg.timestamp + ".");
			}
			
			if (getMethod == 1) {
				if (msg.readOrigin) {
					if (msg.currIter < msg.M) {
						self.tell(new GetMsg(msg.k, msg.currIter + 1, msg.M), self);
					} else {
						long my_runtime = System.currentTimeMillis()-start;
						log_info("Time to die. Execution time: " + my_runtime + "ms.");
						main.tell(new CallOfTheVoid(my_runtime), self);
					}
				} else {
					if (msg.currIter < msg.M) {
						self.tell(new PutMsg(msg.k, msg.v + msg.M, msg.launchGet, msg.currIter+1, msg.M), self);
					} else if (msg.launchGet) {
						self.tell(new GetMsg(msg.k, 0, msg.M), self);
					}
				}
			} else {
				if (msg.currIter < msg.M) {
					if (msg.readOrigin) {
						self.tell(new PutMsg(msg.k, msg.v + msg.M, msg.launchGet, msg.currIter+1, msg.M), self);
					} else {
						self.tell(new GetMsg(msg.k, msg.currIter+1, msg.M), self);
					}
				} else {
					long my_runtime = System.currentTimeMillis()-start;
					log_info("Time to die. Execution time: " + my_runtime + "ms.");
					main.tell(new CallOfTheVoid(my_runtime), self);
				}
			}
		}
	}

	public void launch(LaunchMsg msg) {
		main = msg.main;
		self.tell(new PutMsg(1, msg.i, msg.launchGet, 1, ((msg.launchGet && getMethod == 0) ? 2*msg.M : msg.M)), self);
	}

	public void onReceive(Object message) throws Throwable {
		output.println("\"" + getSender().path().name() + ";" + self.path().name() + ";" + message + "\\n\" +");
		if (id == 0) {
			if (message instanceof SystemMsg) {
				system = ((SystemMsg) message).system;
				
			} else if (message instanceof Members) {
				Members m = (Members) message;
				processes = m;
				log_info("Received other processes' info.");
				if (id == 0) {
					for (ActorRef ref : processes.references.keySet()) {
						processes_times.put(ref, (long) 0);
					}
				}
				
			} else if (message instanceof CallOfTheVoid && curr_ack_void < N-f) {
				processes_times.replace(getSender(), ((CallOfTheVoid) message).runtime);
				curr_ack_void++;
				
				if (curr_ack_void >= N-f) {
					log_info("Sending Poison Pills.");
					long my_runtime = System.currentTimeMillis()-start;
					
					for (ActorRef ref : processes.references.keySet()) {
						output.println("\"" + self.path().name() + ";" + ref.path().name() + ";" + "<PoisonPill()>.\"");
						ref.tell(PoisonPill.getInstance(), self);
					} self.tell(PoisonPill.getInstance(), self);
					output.println("\"" + self.path().name() + ";" + self.path().name() + ";" + "Goodbye Universe.\"");
					output.close();
					log_info("Goodbye Universe.");
					
					
					log_info("Total System Runtime: " + my_runtime + "ms.");
					
					log_info("Time performances:");
					output_perfs.println("Time performances:\n");
					for (Map.Entry<ActorRef, Long> entry : processes_times.entrySet()) {
						if (entry.getValue() == 0) {
							log_info(entry.getKey().path().name() + ": faulty <=> system runtime (" + my_runtime + "ms)");
							output_perfs.println(entry.getKey().path().name() + ": faulty <=> system runtime (" + my_runtime + "ms)");
						} else {
							log_info(entry.getKey().path().name() + ": " + entry.getValue() + "ms");
							output_perfs.println(entry.getKey().path().name() + ": " + entry.getValue() + "ms");
						}
					}
					output_perfs.close();
					
					File htmlFile = new File("doc/visualisation.html");
					Desktop.getDesktop().browse(htmlFile.toURI());
					
					Thread.sleep(100);
					system.terminate();
				}
			}
		} else {
			if (state == -1) {
				// log_info("Faulty. Couldn't process message: " + message);
			} else {
				if (message instanceof Members) {
					Members m = (Members) message;
					processes = m;
					log_info("Received other processes' info.");
				}

				if (message instanceof FaultyMsg) {
					log_info("Becoming faulty.");
					this.setFaulty((FaultyMsg) message);
				}

				if (message instanceof LaunchMsg) {
					log_info("Launching. Starting sequential puts (followed by sequential gets).");
					LaunchMsg m = (LaunchMsg) message;
					this.launch(m);
					
				} else if (message instanceof PutMsg) {
					PutMsg m = (PutMsg) message;
					this.putReceived(m, getSender());
				}

				else if (message instanceof GetMsg) {
					GetMsg m = (GetMsg) message;
					this.getReceived(m, getSender());
				}

				else if (message instanceof WriteMsg) {
					WriteMsg m = (WriteMsg) message;
					this.writeReceived(m, getSender());
				}

				else if (message instanceof ReadAckReqMsg) {
					ReadAckReqMsg m = (ReadAckReqMsg) message;
					this.readAckReqReceived(m, getSender());
				}

				else if (message instanceof WriteAckReqMsg) {
					WriteAckReqMsg m = (WriteAckReqMsg) message;
					this.writeAckReqReceived(m, getSender());
				}

				else if (message instanceof WriteAckMsg) {
					if (state == 1) {
						WriteAckMsg m = (WriteAckMsg) message;
						if (m.currOperation == curr_operation) {
							this.confirmPut(m, getSender());
						} else {
							//
						}
					}
				}
				
				else if (message instanceof ReadAckMsg) {
					if (state == 2) {
						ReadAckMsg m = (ReadAckMsg) message;
						if (m.currOperation == curr_operation) {
							this.confirmGet(m, getSender());
						} else {
							//
						}
					}
				}
				
				else if (message instanceof WriteFinishedMsg) {
					if (state == 3) {
						WriteFinishedMsg m = (WriteFinishedMsg) message;
						if (m.currOperation == curr_operation) {
							this.writeFinishedReceived(m, getSender());
						} else {
							//
						}
					}
				}
			}
		}
	}
}
