package com.example;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class Members {
	public final ArrayList<ActorRef> references;
	public final String print_string;

	public Members(ArrayList<ActorRef> references) {
		this.references = references;
		String s = "[ ";
		for (ActorRef a : references) {
			s += a.path().name() + " ";
		}
		s += " ]";
		print_string = s;
	}

	public String toString() {
		return print_string;
	}
}

class FaultyMsg {
	public FaultyMsg() {

	}

	public String toString() {
		return "<Faulty()>";
	}
}

class GetMsg {
	public Integer k;

	public Integer currIter;
	public Integer M;

	public GetMsg(Integer _k) {
		k = _k;
		currIter = 0;
		M = 0;
	}

	public GetMsg(Integer _k, Integer _currIter, Integer _M) {
		k = _k;
		currIter = _currIter;
		M = _M;
	}

	public String toString() {
		return "<Get(" + k + ", " + currIter + ", " + M + ")>";
	}
}

class ReadAckReqMsg {
	public Integer k;
	public Integer currRead;

	public Integer currIter;
	public Integer M;

	public ReadAckReqMsg(Integer _k, Integer _currRead, Integer _currIter, Integer _M) {
		k = _k;
		currRead = _currRead;
		currIter = _currIter;
		M = _M;
	}

	public String toString() {
		return "<ReadAckReq(" + k + ", " + currRead + ")>";
	}
}

class ReadAckMsg {
	public Integer k;
	public Integer v;
	public Integer timestamp;
	public Integer currOperation;

	public Integer currIter;
	public Integer M;

	public ReadAckMsg(Integer _k, Integer _v, Integer _timestamp, Integer _currRead, Integer _currIter, Integer _M) {
		k = _k;
		v = _v;
		timestamp = _timestamp;
		currOperation = _currRead;
		currIter = _currIter;
		M = _M;
	}

	public String toString() {
		return "<ReadAck(" + k + ", " + v + ", " + timestamp + ", " + currOperation + ")>";
	}
}

class PutMsg {
	public Integer k;
	public Integer v;
	public Boolean launchGet;
	public Integer currIter;
	public Integer M;

	public PutMsg(Integer _k, Integer _v) {
		k = _k;
		v = _v;
		launchGet = false;
		currIter = 0;
		M = 0;
	}

	public PutMsg(Integer _k, Integer _v, Boolean _launchGet, Integer _currIter, Integer _M) {
		k = _k;
		v = _v;
		launchGet = _launchGet;
		currIter = _currIter;
		M = _M;
	}

	public String toString() {
		return "<Put(" + k + ", " + v + ", " + launchGet + ", " + currIter + ", " + M + ")>";
	}
}

class WriteAckReqMsg {
	public Integer k;
	public Integer v;
	public Integer currOperation;

	public Boolean launchGet;
	public Integer currIter;
	public Integer M;

	public WriteAckReqMsg(Integer _k, Integer _v, Integer _currOperation, Boolean _launchGet, Integer _currIter,
			Integer _M) {
		k = _k;
		v = _v;
		currOperation = _currOperation;
		launchGet = _launchGet;
		currIter = _currIter;
		M = _M;
	}

	public String toString() {
		return "<WriteAckReq(" + k + ", " + v + ", " + currOperation + ")>";
	}
}

class WriteAckMsg {
	public Integer k;
	public Integer v;
	public Integer my_v;
	public Integer timestamp;
	public Integer currOperation;

	public Boolean launchGet;
	public Integer currIter;
	public Integer M;

	public WriteAckMsg(Integer _k, Integer _v, Integer _my_v, Integer _timestamp, Integer _currOperation,
			Boolean _launchGet, Integer _currIter, Integer _M) {
		k = _k;
		v = _v;
		my_v = _my_v;
		timestamp = _timestamp;
		currOperation = _currOperation;
		launchGet = _launchGet;
		currIter = _currIter;
		M = _M;
	}

	public String toString() {
		return "<WriteAck(" + k + ", " + v + ", " + timestamp + ", " + currOperation + ")>";
	}
}

class WriteMsg {
	public Integer k;
	public Integer v;
	public Integer timestamp;
	public Boolean readOrigin;
	public Integer currOperation;

	public Boolean launchGet;
	public Integer currIter;
	public Integer M;

	public WriteMsg(Integer _k, Integer _v, Integer _timestamp, Boolean _readOrigin, Integer _currOperation,
			Boolean _launchGet, Integer _currIter, Integer _M) {
		k = _k;
		v = _v;
		timestamp = _timestamp;
		readOrigin = _readOrigin;
		currOperation = _currOperation;
		launchGet = _launchGet;
		currIter = _currIter;
		M = _M;
	}

	public String toString() {
		return "<Write(" + k + ", " + v + ", " + timestamp + ")>";
	}
}

class WriteFinishedMsg {
	public Integer k;
	public Integer v;
	public Integer timestamp;
	public Boolean readOrigin;
	public Integer currOperation;

	public Boolean launchGet;
	public Integer currIter;
	public Integer M;

	public WriteFinishedMsg(Integer _k, Integer _v, Integer _timestamp, Boolean _readOrigin, Integer _currOperation,
			Boolean _launchGet, Integer _currIter, Integer _M) {
		k = _k;
		v = _v;
		timestamp = _timestamp;
		readOrigin = _readOrigin;
		currOperation = _currOperation;
		launchGet = _launchGet;
		currIter = _currIter;
		M = _M;
	}

	public String toString() {
		return "<WriteFinished()>";
	}
}

class LaunchMsg {
	public Integer M;
	public Integer k;
	public Integer i;
	public static Boolean launchGet;

	public LaunchMsg(Integer _k, Integer _M, Integer _i, Boolean _launchGet) {
		k = _k;
		M = _M;
		i = _i;
		launchGet = _launchGet;
	}

	public String toString() {
		return "<Launch(" + M + ", " + i + ">";
	}
}

public class Main {

	public static int N = 3;
	public static int f = 1;
	
	public static int M = 3;
	
	public static Boolean launchGet = true;
	public static int getMethod = 0;

	public static void main(String[] args) throws InterruptedException {
		Set<String> authorized_true = new HashSet<String>();
		authorized_true.add("y");
		authorized_true.add("true");
		authorized_true.add("1");

		Set<String> authorized_false = new HashSet<String>();
		authorized_false.add("n");
		authorized_false.add("false");
		authorized_false.add("0");

		int args_max = 4;
		if (args.length > args_max) {
			System.out.println("\033[0;31mERROR: Illegal number of arguments (" + args_max + " max).");
		} else {

			if (args.length == args_max) {
				try {
					getMethod = Integer.parseInt(args[args_max - 1]);
					if (getMethod != 0 && getMethod != 1) {
						throw new NumberFormatException();
					}					
				} catch (NumberFormatException e) {
					System.out.println("\033[0;31mERROR: Fourth argument (getMethod) incorrect (must be 0/1).");
					return;
				}
			}
			if (args.length >= args_max - 1) {
				if (authorized_true.contains((args[args_max - 2].toLowerCase()))) {
					launchGet = true;
				} else if (authorized_false.contains((args[args_max - 2].toLowerCase()))) {
					launchGet = false;
				} else {
					System.out.println(
							"\033[0;31mERROR: Third argument (launchGet) incorrect (must be y/n, true/false or 1/0).");
					return;
				}
			}
			if (args.length >= args_max - 2) {
				try {
					M = Integer.parseInt(args[args_max - 3]);
					if (M < 1) {
						throw new NumberFormatException();
					}
				} catch (NumberFormatException e) {
					System.out.println("\033[0;31mERROR: Second argument (M) incorrect (must be a strictly positive integer).");
					return;
				}
			} if (args.length >= args_max - 3) {
				try {
					N = Integer.parseInt(args[args_max - 4]);
					if (N < 1) {
						throw new NumberFormatException();
					} f = (int) (N / 2);
				} catch (NumberFormatException e) {
					System.out.println("\033[0;31mERROR: First argument (N) incorrect (must be a strictly positive integer).");
					return;
				}
			}
			// Instantiates an actor system
			final ActorSystem system = ActorSystem.create("system");
			system.log().info("System started with N=" + N);

			ArrayList<ActorRef> references = new ArrayList<>();

			for (int i = 0; i < N; i++) {
				// Instantiates processes
				final ActorRef a = system.actorOf(MyActor.createActor(i + 1, N, f, getMethod), "" + i);
				references.add(a);
			}

			// Gives each process references to all other processes
			Members m = new Members(references);
			for (ActorRef actor : references) {
				actor.tell(m, ActorRef.noSender());
			}

			List<Integer> actor_indices = IntStream.rangeClosed(0, N - 1).boxed().collect(Collectors.toList());
			Collections.shuffle(actor_indices);

			for (int i = 0; i < f; i++) {
				references.get(actor_indices.get(i)).tell(new FaultyMsg(), ActorRef.noSender());
			}
			for (int i = f; i < N; i++) {
				references.get(actor_indices.get(i)).tell(new LaunchMsg(1, N, i, launchGet), ActorRef.noSender());
			}
		}
	}
}
