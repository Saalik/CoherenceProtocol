package model;

import java.util.ArrayList;
import java.util.List;

//mport model.LineState.cacheSlotState;
import model.LineState.cacheSlotState;
import model.Request.cmd_t;

/**
 * This class implements a L1 WTI controller.
 * 
 * @author QLM
 */
public class L1WtiController extends L1Controller {
	
	private enum FsmState {
		FSM_IDLE,
		FSM_INVAL,
		FSM_MISS,
		FSM_SEND_WRITE,
		FSM_MISS_WAIT,
	}
	
	/**
	 * Registers for saving information between states
	 */
	private boolean r_ignore_rsp; // ignore next response when receiving it
	private boolean r_rsp_miss_ok; // response to the miss has been received (set by "rsp_fsm")
	private boolean r_update_cache; // Needs to update the cache after a write since it contains a valid copy
	
	/**
	 * Channels
	 */
	private Channel p_in_req; // incoming coherence requests from ram
	private Channel p_out_rsp; // outgoing coherence responses to ram
	private Channel p_out_req; // outgoing direct requests to ram
	private Channel p_in_rsp; // incoming direct responses from ram
	private Channel p_in_iss_req; // incoming processor requests
	private Channel p_out_iss_rsp; // outgoing processor responses
	
	private FsmState r_fsm_state;
	private FsmState r_fsm_prev_state; // state to which to return after having treated an invalidation
	
	/**
	 * Last coherence request received from the ram, written by method getRequest()
	 */
	private Request m_req;
	/**
	 * Last direct response received from the ram, written by method getResponse()
	 */
	private Request m_rsp;
	/**
	 * Last processor request received from the ram, written by method getIssRequest()
	 */
	private Request m_iss_req;
	

	public L1WtiController(String name, int procid, int nways, int nsets, int nwords, Channel req_to_mem, Channel rsp_from_mem, Channel req_from_mem,
			Channel rsp_to_mem, Channel req_from_iss, Channel rsp_to_iss) {
		r_procid = procid;
		r_srcid = l1StartId + procid;
		m_words = nwords;
		m_name = name;
		m_cycle = 0;
		p_in_req = req_from_mem;
		p_out_rsp = rsp_to_mem;
		p_out_req = req_to_mem;
		p_in_rsp = rsp_from_mem;
		p_in_iss_req = req_from_iss;
		p_out_iss_rsp = rsp_to_iss;
		m_cache_l1 = new CacheL1("CacheL1", procid, nways, nsets, nwords);
		p_in_req.addTgtidTranslation(r_srcid, this); // Translation r_srcid (real unique srcid) to channel index
		p_in_rsp.addTgtidTranslation(r_srcid, this);
		p_in_iss_req.addTgtidTranslation(r_srcid, this);
		reset();
	}
	

	void reset() {
		r_fsm_state = FsmState.FSM_IDLE;
		r_fsm_prev_state = FsmState.FSM_IDLE;
		r_ignore_rsp = false;
		r_rsp_miss_ok = false;
		m_cycle = 0;
	}
	

	/**
	 * Reads the next processor request. The request read is placed into the m_iss_req member structure. Must be called only if p_in_iss_req.empty(this) ==
	 * false Note: This function can be called twice for the same request (it does not consumes the request) so addToFinishedReqs can be called twice.
	 */
	private void getIssRequest() {
		m_iss_req = p_in_iss_req.front(this);
		m_iss_req.setStartCycle(m_cycle); // Must be done here since proc requests can be added before simulation starts
		p_in_iss_req.addToFinishedReqs(this);
		System.out.println(m_name + " gets:\n" + m_iss_req);
	}
	

	/**
	 * Sends a response to the processor and consumes the request in p_in_iss_req.
	 * 
	 * @param addr
	 *            The address of the reponse
	 * @param type
	 *            Type of the response
	 * @param data
	 *            Data value if the type of the response is RSP_READ_WORD
	 */
	private void sendIssResponse(long addr, cmd_t type, long data) {
		p_in_iss_req.popFront(this); // remove request from channel
		List<Long> l = new ArrayList<Long>();
		l.add(data);
		Request req;
		if (type == cmd_t.RSP_WRITE_WORD) {
			req = new Request(addr, r_srcid, // srcid
					r_procid, // targetid (srcid of the proc)
					type, // cmd
					m_cycle, // start cycle
					0); // max duration
		}
		else if (type == cmd_t.RSP_READ_WORD) {
			req = new Request(addr, r_srcid, // srcid
					r_procid, // targetid (srcid of the proc)
					type, // cmd
					m_cycle, // start cycle
					0, // max_duration
					l, // data
					0xF); // be
		}
		else {
			req = null; // avoid error
			assert (false);
		}
		p_out_iss_rsp.pushBack(req);
		m_iss_req = null;
	}
	

	/**
	 * Reads and pops the next coherence request from a ram. The request read is placed into the m_req member structure. Must be called only if
	 * p_in_req.empty(this) == false
	 */
	private void getRequest() {
		m_req = p_in_req.front(this);
		assert (m_req.getNwords() == 0);
		p_in_req.popFront(this);
		System.out.println(m_name + " gets req:\n" + m_req);
	}
	

	/**
	 * Reads and pops the next direct response from a ram. The response read is placed into the m_rsp member structure. Must be called only if
	 * p_in_rsp.empty(this) == false
	 */
	private void getResponse() {
		m_rsp = p_in_rsp.front(this);
		p_in_rsp.popFront(this);
		System.out.println(m_name + " gets rsp:\n" + m_rsp);
	}
	

	/**
	 * Sends a request on a full line
	 * 
	 * @param addr
	 *            Address of the request
	 * @param type
	 *            Type of the request
	 * @param data
	 *            Values to update if appropriated, null otherwise
	 */
	private void sendRequest(long addr, cmd_t type, List<Long> data) {
		Request req = new Request(addr, r_srcid, -1, type, m_cycle, 3, data, 0xF);
		p_out_req.pushBack(req);
		System.out.println(m_name + " sends req:\n" + req);
	}
	

	/**
	 * Sends a request on a word
	 * 
	 * @param addr
	 *            Address of the request
	 * @param type
	 *            Type of the request
	 * @param wdata
	 *            Value to write if any
	 * @param be
	 *            Byte Enable in case of write
	 */
	private void sendRequest(long addr, cmd_t type, Long wdata, int be) {
		List<Long> data = new ArrayList<Long>();
		data.add(wdata);
		Request req = new Request(addr, r_srcid, -1, type, m_cycle, 3, data, be);
		p_out_req.pushBack(req);
		System.out.println(m_name + " sends req:\n" + req);
	}
	

	/**
	 * Sends a response to a coherence request
	 * 
	 * @param addr
	 *            Address targeted by the coherence request
	 * @param tgtid
	 *            srcid of the ram responsible for the coherence request
	 * @param type
	 *            Type of the response
	 * @param rdata
	 *            up-to-date values for the line in case of a write-back in the response, null otherwise
	 */
	private void sendResponse(long addr, int tgtid, cmd_t type, List<Long> rdata) {
		Request rsp = new Request(addr, r_srcid, tgtid, type, m_cycle, 3, rdata, 0xF);
		p_out_rsp.pushBack(rsp);
		System.out.println(m_name + " sends rsp:\n" + rsp);
	}
	

	public void simulate1Cycle() {
		LineState state = new LineState();
		List<Long> data = new ArrayList<Long>();

		switch (r_fsm_state) {

		case FSM_IDLE:
			if (!p_in_req.empty(this)) {

				getRequest();

				if(m_req.getCmd() == cmd_t.INVAL){
					r_fsm_state = FsmState.FSM_INVAL;
					r_fsm_prev_state = FsmState.FSM_IDLE;
					break;
				}
				/* If i'm still here I have no request from mem*/
				/* The proc's turn now*/


			}

			System.out.println("Mem: J'ai rien");

			if (p_in_iss_req.empty(this)) {
				System.out.println("Proc: J'ai rien non plus Bye!");
				break;
			}

			getIssRequest();

			if(m_iss_req.getCmd() == cmd_t.READ_WORD){
				
				System.out.println("Je read words");
				
				if(m_cache_l1.read(m_iss_req.getAddress(), data, state)){
					System.out.println("HIT");
					if( state.state == cacheSlotState.VALID){
						sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_READ_WORD, data.get(0));
					}else if (state.state == cacheSlotState.ZOMBI){
						r_fsm_state=FsmState.FSM_MISS;
						System.out.println("MISS");
					}
					break;
				}else{
					r_fsm_state=FsmState.FSM_MISS;
					System.out.println("MISS");
					break;
				}
			}

			if(m_iss_req.getCmd() == cmd_t.WRITE_WORD){
				r_fsm_state = FsmState.FSM_SEND_WRITE;
				System.out.println("WRITE");
				break;
			}

			/*Finished processing proc request */

			break;

		case FSM_INVAL:
			assert(r_fsm_state == FsmState.FSM_INVAL);
			CacheAccessResult res = m_cache_l1.inval(m_req.getAddress(), true);
			if (res.victimDirty) {
				System.out.println("muh dirty L1 cache invalidation");
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_INVAL_DIRTY, m_req.getData());
			}
			else {
				System.out.println("muh clean L1 cache invalidation");
				sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_INVAL_CLEAN, m_req.getData());
			}
			r_fsm_state = r_fsm_prev_state; 
			break;

		case FSM_MISS:
			System.out.println("IN_MISS");
			sendRequest(m_iss_req.getAddress(),cmd_t.READ_LINE, m_iss_req.getData());
			r_fsm_state = FsmState.FSM_MISS_WAIT;
			break;

		case FSM_SEND_WRITE:
			System.out.println("in send write");
			sendRequest(m_iss_req.getAddress(), cmd_t.WRITE_WORD, m_iss_req.getData().get(0), m_iss_req.getBe());
			sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_WRITE_WORD, m_iss_req.getData().get(0));
			r_fsm_state = FsmState.FSM_IDLE;
			break;

		case FSM_MISS_WAIT:
			System.out.println("IN_MISS_WAIT");
			if (r_rsp_miss_ok){
				System.out.println("i");
				m_cache_l1.writeLine(m_rsp.getAddress(), m_rsp.getData(), false);
				r_fsm_state = FsmState.FSM_IDLE;
			}
			break;

		default:
			assert (false);
			break;
		} // end switch(r_fsm_state)

		System.out.println(m_name + " next state: " + r_fsm_state);

		// Following code equivalent to a 1-state FSM executing in parallel
		// which is in charge of consuming the responses on the p_in_rsp_port (r_fsm_rsp)
		// and updating synchronization registers
		if (!p_in_rsp.empty(this)) {
			getResponse();
			if (m_rsp.getCmd() == cmd_t.RSP_READ_LINE) {
				// Response to the miss received, we can unblock the r_fsm_state via the r_rsp_miss_ok register
				r_rsp_miss_ok = true;
			}
			else if (m_rsp.getCmd() == cmd_t.RSP_WRITE_WORD) {
				// Nothing special to do
			}
			else {
				assert (false);
			}
		}
		m_cycle++;
	}


	public int getSrcid() {
		return r_srcid;
	}


	public String getName() {
		return m_name;
	}
}
