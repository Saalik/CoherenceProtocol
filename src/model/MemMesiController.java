package model;

import java.util.List;
import java.util.Vector;

import utils.Utile;

import model.Ram.BlockState;
import model.Request.cmd_t;

/**
 * This class implements the memory controller for the MESI protocol.
 * 
 * @author QLM
 */
public class MemMesiController implements MemController {
	
	private enum FsmState {
		FSM_IDLE,
		FSM_READ_LINE,
		FSM_GETM,
        FSM_WRITE_LINE,
		FSM_INVAL,
		FSM_INVAL_SEND,
		FSM_INVAL_WAIT,
		FSM_DIR_UPDATE,
		FSM_RSP_GETM,
		FSM_RSP_READ,
	}
	
	
	/**
	 * Initiator Index
	 */
	private int m_srcid;
	/**
	 * Number of words in a line
	 */
	private int m_words;
	/**
	 * Current cycle
	 */
	private int m_cycle;
	
	/**
	 * Srcid offset for Ram elements
	 */
	private final static int memStartId = 100;
	
	private String m_name;
	
	private Ram m_ram;
	
	/**
	 * Channels
	 */
	private Channel p_in_req; // direct requests coming from the caches
	private Channel p_out_rsp; // responses to direct requests
	private Channel p_out_req; // coherence requests sent to caches
	private Channel p_in_rsp; // responses to coherence requests
	
	private CopiesList m_req_copies_list;
	private CopiesList m_rsp_copies_list;
	
	/***
	 * FSM state
	 */
	private FsmState r_fsm_state;
	
	/**
	 * Registers used for saving information from one state to another
	 */
	private boolean r_rsp_full_line;
	private boolean r_write_back;
	private cmd_t r_rsp_type;
	
	/**
	 * Last direct request received from a L1 cache, written by method getRequest()
	 */
	private Request m_req;
	/**
	 * Last coherence response received from a L1 cache; written by method getResponse()
	 */
	private Request m_rsp;
	
	
	private long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}
	

	public MemMesiController(String name, int id, int nwords, Vector<Segment> seglist, Channel req_to_mem, Channel rsp_from_mem, Channel req_from_mem,
			Channel rsp_to_mem) {
		m_srcid = id + memStartId; // id is the id among the memories
		m_words = nwords;
		m_name = name;
		m_cycle = 0;
		p_in_req = req_to_mem;
		p_out_rsp = rsp_from_mem;
		p_out_req = req_from_mem;
		p_in_rsp = rsp_to_mem;
		m_ram = new Ram("Ram", nwords, seglist);
		for (Segment seg : seglist) {
			seg.setTgtid(m_srcid);
		}
		p_in_req.addAddrTranslation(seglist, this);
		p_in_rsp.addTgtidTranslation(m_srcid, this);
		reset();
	}
	

	void reset() {
		r_fsm_state = FsmState.FSM_IDLE;
		r_rsp_full_line = false;
		r_rsp_type = cmd_t.NOP;
		r_write_back = false;
		m_cycle = 0;
	}
	

	/**
	 * Reads and pops the next direct request from a L1 cache. The request read is placed into the m_req member structure. Must be called only if
	 * p_in_req.empty(this) == false
	 */
	private void getRequest() {
		m_req = p_in_req.front(this);
		p_in_req.popFront(this);
		System.out.println(m_name + " receives req:\n" + m_req);
	}
	

	/**
	 * Reads and pops the next coherence response from a L1 cache. The response read is placed into the m_rsp member structure. Must be called only if
	 * p_in_rsp.empty(this) == false
	 */
	private void getResponse() {
		m_rsp = p_in_rsp.front(this);
		p_in_rsp.popFront(this);
		System.out.println(m_name + " receives rsp:\n" + m_rsp);
	}
	

	/**
	 * Sends a coherence request to a L1 cache.
	 * 
	 * @param addr
	 *            The address of the request (e.g. address to invalidate)
	 * @param targetid
	 *            srcid of the L1 cache to which send the request
	 * @param type
	 *            Type of the coherence request
	 */
	private void sendRequest(long addr, int targetid, cmd_t type) {
		Request req = new Request(addr, m_srcid, targetid, type, m_cycle, 3);
		p_out_req.pushBack(req);
		System.out.println(m_name + " sends req:\n" + req);
	}
	

	/**
	 * Sends a direct response to a L1 cache.
	 * 
	 * @param addr
	 *            The address of the request
	 * @param targetid
	 *            srcid of the L1 cache to which send the response
	 * @param type
	 *            Type of the response
	 * @param rdata
	 *            Data associated with the response (typically, copy of a line)
	 */
	private void sendResponse(long addr, int targetid, cmd_t type, List<Long> rdata) {
		Request rsp = new Request(addr, m_srcid, targetid, type, m_cycle, 3, // max_duration
				rdata, 0xF);
		p_out_rsp.pushBack(rsp);
		System.out.println(m_name + " sends rsp:\n" + rsp);
	}
	

	public void simulate1Cycle() {
		
		switch (r_fsm_state) {
		
		case FSM_IDLE:
			r_rsp_type = cmd_t.NOP;
			
			if (p_in_req.empty(this)) {
				break;
			}
			getRequest();
			
			assert (m_req.getNwords() == m_words || m_req.getNwords() == 1 || m_req.getNwords() == 0);
			assert (m_ram.containsAddr(m_req.getAddress()));
			assert (m_req.getAddress() == align(m_req.getAddress()));
			
			if (m_req.getCmd() == cmd_t.WRITE_LINE) {
				r_fsm_state = FsmState.FSM_WRITE_LINE;
			}
			else if (m_req.getCmd() == cmd_t.READ_LINE) {
				r_fsm_state = FsmState.FSM_READ_LINE;
			}
			else if (m_req.getCmd() == cmd_t.GETM || m_req.getCmd() == cmd_t.GETM_LINE) {
				r_fsm_state = FsmState.FSM_GETM;
			}
			else {
				assert (false);
			}
			break;
		

		case FSM_READ_LINE:
			if ((m_ram.isMod(m_req.getAddress()) || m_ram.isExclu(m_req.getAddress())) && !m_ram.hasCopy(m_req.getAddress(), m_req.getSrcid())) {
				// Block modified or exclusive and owned by another processor
				// write-back operation possibly required
				r_fsm_state = FsmState.FSM_INVAL;
			}
			else {
				// We authorize an access in exclusive if there is no other copy
				r_fsm_state = FsmState.FSM_DIR_UPDATE;
			}
			break;
		

		case FSM_GETM:
			// manages GETM_LINE too
			
			// following assert meaning that the requester cannot own the line in Modified
			// assert (!m_ram.isMod(m_req.getAddress()) || !m_ram.hasCopy(m_req.getAddress(), m_req.getSrcid()));
			
			// Yet, this is not true if an inval request arrives after the GETM and its response passes the GETM;
			// The GETM will be considered as a legit GETM by the memory, which will send an inval to the cache responsible for the inval
			// When the memory will respond the cache, it will mark the cache as owning the line in M
			// Upon response, the cache will ignore and retry, and will fall in this case
			
			r_rsp_full_line = m_req.getCmd() == cmd_t.GETM_LINE;
			//assert(!(m_req.getCmd() == cmd_t.GETM && m_ram.isMod(m_req.getAddress())));
			if (!m_ram.hasOtherCopy(m_req.getAddress(), m_req.getSrcid())) {
				// No inval to send
				r_fsm_state = FsmState.FSM_DIR_UPDATE;
			}
			else {
				r_fsm_state = FsmState.FSM_INVAL;
			}
			break;
		

		case FSM_WRITE_LINE:
			sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_WRITE_LINE, null); // We can respond now
			if ((m_ram.isMod(m_req.getAddress()) || m_ram.isExclu(m_req.getAddress())) && m_ram.hasCopy(m_req.getAddress(), m_req.getSrcid())) {
				m_ram.writeLine(m_req.getAddress(), m_req.getData());
				r_fsm_state = FsmState.FSM_DIR_UPDATE;
			}
			else {
				// Case in which a write-back has been passed by an invalidation
				// request on the same line;
				// the processor is not marked anymore as having a copy (or write permission), and we
				// must ignore this request
				// because the data sent aren't up to date anymore (risk that
				// they have been modified since the first write-back)
				r_fsm_state = FsmState.FSM_IDLE;
			}
			break;
			
		
		case FSM_INVAL:
			m_req_copies_list = new CopiesList(m_ram.getCopies(m_req.getAddress()));
			m_rsp_copies_list = new CopiesList(m_ram.getCopies(m_req.getAddress())); // note : il s'agit bien de m_req
			m_req_copies_list.remove(m_req.getSrcid());
			m_rsp_copies_list.remove(m_req.getSrcid());
			
			if (m_req.getCmd() == cmd_t.READ_LINE) {
				r_rsp_type = cmd_t.INVAL_RO; // RO-invalidation
			}
			else if (m_req.getCmd() == cmd_t.GETM || m_req.getCmd() == cmd_t.GETM_LINE) {
				r_rsp_type = cmd_t.INVAL;
			}
			else {
				assert (false);
			}
			r_write_back = m_ram.isExclu(m_req.getAddress()) || m_ram.isMod(m_req.getAddress());
			r_fsm_state = FsmState.FSM_INVAL_SEND;
			break;
			
		
		case FSM_INVAL_SEND:
		{
			int targetid = m_req_copies_list.getNextOwner();
			m_req_copies_list.remove(targetid);
			// We align the address for the invalidation request
			sendRequest(align(m_req.getAddress()), targetid, r_rsp_type);
			
			if (m_req_copies_list.nbCopies() == 0) {
				r_fsm_state = FsmState.FSM_INVAL_WAIT;
			}
		}
		break;
		
		
		case FSM_INVAL_WAIT:
			if (!p_in_rsp.empty(this)) {
				getResponse();
				assert (m_rsp.getAddress() == m_req.getAddress());
				assert (m_rsp_copies_list.hasCopy(m_rsp.getSrcid()));
				m_rsp_copies_list.remove(m_rsp.getSrcid());
				
				if (m_rsp.getCmd() == cmd_t.RSP_INVAL_DIRTY || m_rsp.getCmd() == cmd_t.RSP_INVAL_RO_DIRTY) {
					// Write in memory the cache line only if it is a write-back
					// (the line could be in EXCLUSIVE state and not modified)
					assert (r_write_back);
					m_ram.writeLine(m_rsp.getAddress(), m_rsp.getData());
					r_write_back = false;
				}
				if (m_rsp_copies_list.nbCopies() == 0) {
					r_fsm_state = FsmState.FSM_DIR_UPDATE;
				}
			}
			break;
		

		case FSM_DIR_UPDATE:
			if (m_req.getCmd() == cmd_t.READ_LINE) {
				if (m_ram.nbCopies(m_req.getAddress()) == 0) {
					m_ram.setState(m_req.getAddress(), BlockState.EXCLUSIVE);
					r_rsp_type = cmd_t.RSP_READ_LINE_EX;
				}
				else {
					m_ram.setState(m_req.getAddress(), BlockState.VALID);
					r_rsp_type = cmd_t.RSP_READ_LINE; // Line will be in S state
				}
				m_ram.addCopy(m_req.getAddress(), m_req.getSrcid());
				r_fsm_state = FsmState.FSM_RSP_READ;
			}
			else if (m_req.getCmd() == cmd_t.GETM || m_req.getCmd() == cmd_t.GETM_LINE) {
				m_ram.removeAllCopies(m_req.getAddress());
				m_ram.setState(m_req.getAddress(), BlockState.MODIFIED);
				m_ram.addCopy(m_req.getAddress(), m_req.getSrcid());
				r_fsm_state = FsmState.FSM_RSP_GETM;
			}
			else if (m_req.getCmd() == cmd_t.WRITE_LINE) {
				// Write back operation initiated by the processor
				m_ram.removeAllCopies(m_req.getAddress());
				r_fsm_state = FsmState.FSM_IDLE;
			}
			else {
				assert (false);
			}
			break;
		

		case FSM_RSP_GETM:
		{
			if (r_rsp_full_line) {
				r_rsp_full_line = false;
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_GETM_LINE, m_ram.getLine(m_req.getAddress()));
			}
			else {
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_GETM, null);
			}
			r_fsm_state = FsmState.FSM_IDLE;
		}
		break;
		

		case FSM_RSP_READ:
			sendResponse(m_req.getAddress(), m_req.getSrcid(), r_rsp_type, m_ram.getLine(m_req.getAddress()));
			r_fsm_state = FsmState.FSM_IDLE;
			break;
		

		default:
			assert (false);
			break;
		} // end switch(r_fsm_state)
		System.out.println(m_name + " next state: " + r_fsm_state);
		
		m_cycle++;
	}
	

	public int getSrcid() {
		return m_srcid;
	}
	

	public String getName() {
		return m_name;
	}
	
}
