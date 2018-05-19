import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import BIT.highBIT.*;
//import BIT.samples.StatisticsBranch;

public class OurTool {
	private static HashMap<Long, Double> dyn_method_count = new HashMap<>();
	private static HashMap<Long, Double> dyn_bb_count = new HashMap<>();
	private static HashMap<Long, Double> dyn_instr_count = new HashMap<>();

	public static void doDynamic(File in_dir, File out_dir) {
		String filelist[] = in_dir.list();

		for (int i = 0; i < filelist.length; i++) {
			String filename = filelist[i];
			if (filename.endsWith(".class")) {
				String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
				String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
				ClassInfo ci = new ClassInfo(in_filename);
				for (Enumeration<?> e = ci.getRoutines().elements(); e.hasMoreElements();) {
					Routine routine = (Routine) e.nextElement();
					routine.addBefore("OurTool", "dynMethodCount", new Integer(1));

					for (Enumeration<?> b = routine.getBasicBlocks().elements(); b.hasMoreElements();) {
						BasicBlock bb = (BasicBlock) b.nextElement();
						bb.addBefore("OurTool", "dynInstrCount", new Integer(bb.size()));
					}
				}
				ci.addAfter("OurTool", "registerMetric", "null");
				ci.write(out_filename);
			} else if ((new File(filename)).isDirectory()){
				File f = new File(filename);
				doDynamic(f,f);
			}
		}
	}

	public static synchronized void registerMetric(String foo) {
		Long id = Thread.currentThread().getId();
		WebServer.threadMetrics.put(id, dyn_instr_count.get(id));
	}

	public static synchronized void dynInstrCount(int incr) {
		long threadId = Thread.currentThread().getId();
		if (dyn_instr_count.get(threadId) == null) {
			dyn_instr_count.put(threadId, 0.);
		}
		dyn_instr_count.put(threadId, dyn_instr_count.get(threadId) + incr);

		if (dyn_bb_count.get(threadId) == null) {
			dyn_bb_count.put(threadId, 0.);
		}
		dyn_bb_count.put(threadId, dyn_bb_count.get(threadId) + 1);
	}
	
	public static synchronized void dynMethodCount(int incr) {
		long threadId = Thread.currentThread().getId();
		if (dyn_method_count.get(threadId) == null) {
			dyn_method_count.put(threadId, 0.);
		}
		dyn_method_count.put(threadId, dyn_method_count.get(threadId) + incr);
	}

	public static void main(String argv[]) {
		if(argv.length == 0) {
			System.out.println("Please provide the name of the directory");
			return;
		}

		File in_dir = new File(argv[0]);

		if (! in_dir.isDirectory()) {
			System.out.println("Please provide a directory");
			return;
		}

		doDynamic(in_dir, in_dir);
	}
}

