package io.mycat.mycat2.net;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.mycat2.AbstractMySQLSession;
import io.mycat.mycat2.MySQLSession;
import io.mycat.mycat2.MycatSession;
import io.mycat.mycat2.Interceptor.InterceptorSystem;
import io.mycat.mycat2.console.SessionKeyEnum;
import io.mycat.mycat2.sqlparser.BufferSQLParser;
import io.mycat.mysql.packet.MySQLPacket;
import io.mycat.proxy.NIOHandler;
import io.mycat.proxy.ProxyBuffer;

/**
 * 负责MycatSession的NIO事件，驱动SQLCommand命令执行，完成SQL的处理过程
 * 
 * @author wuzhihui
 *
 */
public class DefaultMycatSessionHandler implements NIOHandler<AbstractMySQLSession> {
	public static final DefaultMycatSessionHandler INSTANCE = new DefaultMycatSessionHandler();
	private static Logger logger = LoggerFactory.getLogger(DefaultMycatSessionHandler.class);

	public void onSocketRead(final AbstractMySQLSession session) throws IOException {
		if (session instanceof MycatSession) {
			onFrontRead((MycatSession) session);
		} else {
			onBackendRead((MySQLSession) session);
		}
	}

	private void onFrontRead(final MycatSession session) throws IOException {
		boolean readed = session.readFromChannel();
		ProxyBuffer buffer = session.getProxyBuffer();
		// 在load data的情况下，SESSION_PKG_READ_FLAG会被打开，以不让进行包的完整性检查
		if (!session.getSessionAttrMap().containsKey(SessionKeyEnum.SESSION_PKG_READ_FLAG.getKey())
				&& (readed == false ||
						// 没有读到完整报文
						MySQLSession.CurrPacketType.Full != session.resolveMySQLPackage(buffer, session.curMSQLPackgInf,
								false))) {
			return;
		}
		if (session.curMSQLPackgInf.endPos < buffer.writeIndex) {
			logger.warn("front contains multi package ");
		}
		if(MySQLPacket.COM_QUERY==(byte)session.curMSQLPackgInf.pkgType) {
			BufferSQLParser parser = new BufferSQLParser();
			int rowDataIndex = session.curMSQLPackgInf.startPos + MySQLPacket.packetHeaderSize +1 ;
			int length = session.curMSQLPackgInf.pkgLength -  MySQLPacket.packetHeaderSize - 1 ;
			parser.parse(session.proxyBuffer.getBytes(rowDataIndex, length),session.sqlContext);
		}
		session.curSQLCommand = null;
		session.clearSQLCmdMap();
		InterceptorSystem.INSTANCE.onFrontReadIntercept(session);
		//session.getMyCommand();
//		boolean result = true;
//		final List<Interceptor> interceptorList = MycatSession.interceptorList;
//		for(int i = 0; result  && i < interceptorList.size(); i++) {
//			result = interceptorList.get(i).onFrontReadIntercept(session);
//		}
		
//		MyCommand myCommand = session.getMyCommand();
//		
//		if(myCommand!=null){
//			session.curSQLCommand = myCommand;
//			// 如果当前包需要处理，则交给对应方法处理，否则直接透传
//			if(session.curSQLCommand.procssSQL(session)){
//				session.curSQLCommand.clearFrontResouces(session, false);
//			}
//		}else{
//			logger.error(" current packageTyps is not support,please fix it!!! the packageType is {} ",session.curMSQLPackgInf);
//		}
	}

	private void onBackendRead(MySQLSession session) throws IOException {
		InterceptorSystem.INSTANCE.onBackendReadIntercept(session);

//		boolean result = true;
//		final List<Interceptor> interceptorList = MycatSession.interceptorList;
//		for(int i = interceptorList.size() -1; result  && i >= 0; i--) {
//			result = interceptorList.get(i).onFrontReadIntercept(session);
//		}
////		// 交给SQLComand去处理
//		MyCommand curCmd = session.getMycatSession().curSQLCommand;
//		if (curCmd.onBackendResponse(session)) {
//			curCmd.clearBackendResouces((MySQLSession) session,false);
//		}

	}

	/**
	 * 前端连接关闭后，延迟关闭会话
	 * 
	 * @param userSession
	 * @param normal
	 */
	public void onSocketClosed(AbstractMySQLSession session, boolean normal) {
		if (session instanceof MycatSession) {
			logger.info("front socket closed " + session);
			session.lazyCloseSession(normal, "front closed");
		} else {
			MySQLSession mysqlSession = (MySQLSession) session;
			try {
				InterceptorSystem.INSTANCE.onBackendClosedIntercept(mysqlSession, normal);
				//MyCommand curSQLCommand = (MyCommand)mysqlSession.getMycatSession().getSQLCmd(DefaultIntercepor.INSTANCE);
				//mysqlSession.getMycatSession().curSQLCommand.onBackendClosed(mysqlSession, normal);
				//curSQLCommand.onBackendClosed(mysqlSession, normal);
			} catch (IOException e) {
				logger.warn("caught err ", e);
			}
		}
	}

	@Override
	public void onSocketWrite(AbstractMySQLSession session) throws IOException {
		session.writeToChannel();

	}

	@Override
	public void onConnect(SelectionKey curKey, AbstractMySQLSession session, boolean success, String msg)
			throws IOException {
		throw new java.lang.RuntimeException("not implemented ");
	}

	@Override
	public void onWriteFinished(AbstractMySQLSession session) throws IOException {
		// 交给SQLComand去处理
		if (session instanceof MycatSession) {
			InterceptorSystem.INSTANCE.clearFrontWriteFinishedIntercept((MycatSession) session);
//			MycatSession mycatSs = (MycatSession) session;
//			if (mycatSs.curSQLCommand.onFrontWriteFinished(mycatSs)) {
//				mycatSs.curSQLCommand.clearFrontResouces(mycatSs,false);
//			}
		} else {
//			MycatSession mycatSs = ((MySQLSession) session).getMycatSession();
			InterceptorSystem.INSTANCE.clearBackendResoucesIntercept((MySQLSession) session);
//			if (mycatSs.curSQLCommand.onBackendWriteFinished((MySQLSession) session)) {
//				mycatSs.curSQLCommand.clearBackendResouces((MySQLSession) session,false);
//			}
		}
	}

}
