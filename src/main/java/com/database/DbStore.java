package com.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class DbStore {
	private String url;
	private String user;
	private String password;
	private PreparedStatement pst;
	private String insertQuery;
	private int stackIndex;
	private int STACK_CAP = 250000; //default value
	private boolean autoCommit = false;
	private Connection con;
	private Logger logger;

	public DbStore(boolean autoCommit, int cap){
		logger = LogManager.getLogger(DbStore.class);
        pst = null;
        stackIndex = 0;
        url = "jdbc:mysql://localhost:3306/dataanalyzer?autoReconnect=true&useSSL=false";
        user = "root";
        password = "password";
        insertQuery = "INSERT INTO packetinfo(PacketNumber,"
        					+ "Timestamp,SrcAddress,SrcPort,"
			        		+ "DestAddress,DestPort,"
			        		+ "Protocol,Ack,Syn) "
		        		+ "VALUES(?,?,?,?,?,?,?,?,?)";
        STACK_CAP = cap;
        this.autoCommit = autoCommit;
	}
	
	public boolean insertToDB(long packetNumber, Timestamp timestamp, 
			byte[] srcAddress, int srcPort, byte[] destAddress, int destPort, 
			int protocol, boolean ack, boolean syn){
		boolean result = true;
        try {
        	openConnection();
            
            pst.setLong(1, packetNumber);
            pst.setTimestamp(2, timestamp);
            pst.setBytes(3, srcAddress);
            pst.setInt(4, srcPort);
            pst.setBytes(5, destAddress);
            pst.setInt(6, destPort);
            pst.setInt(7, protocol);
            pst.setBoolean(8, ack);
            pst.setBoolean(9, syn);
            
            pst.addBatch();
            stackIndex++;
            
            if (autoCommit && stackIndex == STACK_CAP) {
            	commitBatch();
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage());
            result = false;
        }
        return result;
    }
	
	public boolean commitBatch() {
		boolean result = false;
		if (stackIndex <= 0) return result;
		try {
			logger.info("commitBatch - Attempting to commit " + stackIndex + " entries.");
			long startTime = System.currentTimeMillis();
			long[] rs = pst.executeLargeBatch();
			con.commit();
			long endTime = System.currentTimeMillis();
			logger.info("commitBatch - commited " + rs.length + " entries in " + (endTime - startTime)/1000 + " seconds.");
        	stackIndex = 0;
        	result = true;
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
		} finally {
			closeConnection();
		}
		return result;
	}
	
	private void closeConnection() {
		 try {
             if (pst != null) {
                 pst.close();
             }
             if (con != null) {
                 con.close();
             }
         } catch (SQLException ex) {
        	 logger.error(ex.getMessage(), ex);
         }
	}
	
	private void openConnection() {
		try {
			if (pst == null || pst.isClosed()) {
				if (con == null || con.isClosed()) {
					con = DriverManager.getConnection(url, user, password);
					con.setAutoCommit(false);
				}
				pst = con.prepareStatement(insertQuery);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void clearDbTable() {
		Connection connection = null;
		Statement st = null;
		try {
			connection = DriverManager.getConnection(url, user, password);
			st = connection.createStatement();
			st.executeUpdate("TRUNCATE packetinfo");
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
		} finally {
			try {
				if (st != null) st.close();
				if (connection != null) connection.close();
			} catch (SQLException e) {
				logger.error(e.getMessage(), e);
			}
		}
		
	}
}