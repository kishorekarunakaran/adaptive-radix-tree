package art;
import org.openjdk.jmh.annotations.*;
import java.util.NavigableMap;
import java.util.concurrent.ThreadLocalRandom;
public class AdaptiveRadixTreeBenchmark {

	@State(Scope.Benchmark) // all benchmarks can share the created ART
	public static class MyState {
		public static final Object holder = new Object();
		public NavigableMap<Integer, Object> art = new AdaptiveRadixTree<>(BinaryComparable.INTEGER);
		public static final int n = 1_000_000; // 1 million integer keys

		@Setup(Level.Trial) // setup required only once per fork run
		public void doSetup(){
			// dense keys
			for(int i = 1; i <= n; i++){
				art.put(i, holder);
			}
		}
	}

	@State(Scope.Thread)
	public static class Lookup {
		int lookup;
		// random lookup
		@Setup(Level.Invocation)
		public void doSetup(){
			lookup = ThreadLocalRandom.current().nextInt(1, MyState.n + 1);
		}
	}

	@BenchmarkMode(Mode.Throughput)
	@Benchmark
	public Object lookups(MyState state, Lookup lookup){
		return state.art.get(lookup.lookup);
	}

}
