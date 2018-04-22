//
// StatisticsTool.java
//
// This program measures and instruments to obtain different statistics
// about Java programs.
//
// Copyright (c) 1998 by Han B. Lee (hanlee@cs.colorado.edu).
// ALL RIGHTS RESERVED.
//
// Permission to use, copy, modify, and distribute this software and its
// documentation for non-commercial purposes is hereby granted provided 
// that this copyright notice appears in all copies.
// 
// This software is provided "as is".  The licensor makes no warrenties, either
// expressed or implied, about its correctness or performance.  The licensor
// shall not be liable for any damages suffered as a result of using
// and modifying this software.

import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import BIT.highBIT.*;
//import BIT.samples.StatisticsBranch;

public class StatisticsTool {
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
					routine.addBefore("StatisticsTool", "dynMethodCount", new Integer(1));

					for (Enumeration<?> b = routine.getBasicBlocks().elements(); b.hasMoreElements();) {
						BasicBlock bb = (BasicBlock) b.nextElement();
						bb.addBefore("StatisticsTool", "dynInstrCount", new Integer(bb.size()));
					}
				}
				ci.addAfter("StatisticsTool", "printDynamic", "null");
				ci.write(out_filename);
			}
		}
	}

	public static synchronized void printDynamic(String foo) {
		WebServer ws = new WebServer();
		Long id = Thread.currentThread().getId();
		ws.addMetric(id, dyn_instr_count.get(id));
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
		File in_dir = new File(argv[0]);
		File out_dir = in_dir;
		if (in_dir.isDirectory())
			doDynamic(in_dir, out_dir);
		else
			return;
	}
}
