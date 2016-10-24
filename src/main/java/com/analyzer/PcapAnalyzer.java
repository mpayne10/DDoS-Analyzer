package com.analyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.database.DbStore;
import com.database.RowContent;

public class PcapAnalyzer {
	
	//private static String srcPcapFile = "C:\\Users\\aavalos\\Downloads\\dataset.pcap";
	private static String srcPcapFile = "C:\\Temp\\dataset.pcap.Packets_0.pcap";
	//private static String srcPcapFile = "C:\\Users\\aavalos\\Documents\\test500.pcap";
	private static int packetSize = 5000000;

	public static void main(String[] args) {
		Logger logger = LogManager.getLogger();
		String dirName = System.getProperty("java.io.tmpdir") + "pcapTmp";
		boolean splitFile = false;
		
		
		File originalfile = new File(srcPcapFile);
		File[] files = null;
		if (originalfile.length() > 1 * 1024 * 1024 * 1024) {
			splitFile = true;
		}
		
		//create DB and clear from any previous results
		String databaseName = originalfile.getName();
		databaseName = databaseName.substring(0, databaseName.indexOf("."));
		DbStore dbStrore = new DbStore(databaseName, true);
		
		dbStrore.clearDbTable();
		dbStrore.closeAllConnections();
		
		
		long startTime = System.currentTimeMillis();
		
		if (splitFile) {
			//Split pcap file
			PcapManager pm = new PcapManager();
			pm.pcapSplitter(srcPcapFile, dirName, packetSize);
			
			//Get the file from tmp dir
			File f = new File(dirName);
			files = f.listFiles();
		} else {
			files = new File[1];
			files[0] = originalfile;
		}
		
		//Parse each file in separate threads
		HashMap<Thread, PcapReader> threadArr = new HashMap<Thread, PcapReader>();
		for(File file: files){
			logger.info("File name: " + file.getName());
			PcapReader pr = new PcapReader(file.getAbsolutePath(), databaseName);
			Thread th = new Thread(pr);
			th.setName(file.getName());
			th.start();
			threadArr.put(th,  pr);
		}
		
		// Wait until every thread finishes
		Iterator<Entry<Thread, PcapReader>> iterator = threadArr.entrySet().iterator();
		try {
			while (iterator.hasNext()) {
				iterator.next().getKey().join();
			}
		} catch (InterruptedException e) {
			logger.error(e.getMessage());
		}
		long endTime = System.currentTimeMillis();
		
		// Get statistics
		long packetsProcessed = 0;
		long packetsRead = 0;
		long ipV4PacketsRead = 0;
		long ipV6PacketsRead = 0;
		long tcpPacketsRead = 0;
		long udpPacketsRead = 0;
		long icmpPacketsRead = 0;
		long unknownPacketsRead = 0;
		long illegalPacketsRead = 0;
		long tcpFloodPacketsRead = 0;
		long udpFloodPacketsRead = 0;
		long icmpFloodPacketsRead = 0;
		iterator = threadArr.entrySet().iterator();
		while (iterator.hasNext()) {
			PcapReader pr = iterator.next().getValue();
			packetsProcessed = packetsProcessed + pr.getPacketsProcessed();
			packetsRead = packetsRead + pr.getPacketsRead();
			ipV4PacketsRead = ipV4PacketsRead + pr.getIpV4pPacketsRead();
			ipV6PacketsRead = ipV6PacketsRead + pr.getIpV6pPacketsRead();
			tcpPacketsRead = tcpPacketsRead + pr.getTcpPacketsRead();
			udpPacketsRead = udpPacketsRead + pr.getUdpPacketsRead();
			icmpPacketsRead = icmpPacketsRead + pr.getIcmpPacketsRead();
			unknownPacketsRead = unknownPacketsRead + pr.getUnknownPacketsRead();
			illegalPacketsRead = illegalPacketsRead + pr.getIllegalPacketsRead();
			tcpFloodPacketsRead = tcpFloodPacketsRead + pr.getTcpFloodPacketsRead();
			udpFloodPacketsRead = udpFloodPacketsRead + pr.getUdpFloodPacketsRead();
			icmpFloodPacketsRead = icmpFloodPacketsRead + pr.getIcmpFloodPacketsRead();
		}
		
		logger.info("All threads completed in: " + (endTime - startTime)/1000 + " seconds");
		logger.info("Packets read: " + packetsRead);
		logger.info("Packets Processed: " + packetsProcessed);
		logger.info("TCP packets Read: " + tcpPacketsRead);
		logger.info("UDP packets Read: " + udpPacketsRead);
		logger.info("ICMP packets Read: " + icmpPacketsRead);
		logger.info("Unknwon packets Read: " + unknownPacketsRead);
		logger.info("Illegal packets Read: " + illegalPacketsRead);
		logger.info("TCP Flood packets Read: " + tcpFloodPacketsRead);
		logger.info("UDP Flood packets Read: " + udpFloodPacketsRead);
		logger.info("ICMP Flood packets Read: " + icmpFloodPacketsRead);
		
		// Delete tmp directory
		try {
			FileUtils.deleteDirectory(new File(dirName));
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
		
		
		Iterator<RowContent> rowIterator = dbStrore.getDosVictims(DbStore.ICMP_FLOODING_TABLE_NAME).iterator();
		int i = 0;
		while (rowIterator.hasNext() && i < 10) {
			i++;
			RowContent rc = rowIterator.next();
			logger.info(rc.getIp());
		}
	}
}
