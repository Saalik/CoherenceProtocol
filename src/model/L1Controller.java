package model;

import utils.Utile;

/**
 * This class provides the common attributes to L1 controllers.
 * It also implements the ... method which ...
 * The l1StartId purpose is to make a correspondence between the cache number (r_id), ranging from 0 to nb_caches -
 * 1, and the srcid on the network.
 */

public abstract class L1Controller implements Module {

	/**
	 * Offset for L1 caches srcid
	 */
	static final int l1StartId = 10;
	
	/**
	 * Global initiator and target index
	 */
	protected int r_srcid;
	
	/**
	 * srcid of the associated processor
	 */
	protected int r_procid;
	
	/**
	 * Number of words in a line
	 */
	protected int m_words;
	
	/**
	 * Current cycle
	 */
	protected int m_cycle;
	
	protected String m_name;
	
	protected CacheL1 m_cache_l1;
	
	
	protected long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}
	
	public void printContent() {
		System.out.println("Cache " + m_name);
		m_cache_l1.printTrace();
	}
	
	
}
