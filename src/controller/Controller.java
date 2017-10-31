package controller;

import java.util.Vector;

import model.Module;
import model.Processor;
import topcells.Topcell;
import view.ModulePosition;
import view.Vue;

/**
 * This class is the toplevel part of the controller. It contains references to all the listeners, the view and the topcell.
 * FIXME: build the ModulePosition objects elsewhere?
 * @author QLM
 * 
 */
public class Controller {
	
	private Vue vue;
	private Topcell topcell;
	
	private NextCycleListener nextCycleListener;
	private ShowProcsListener showProcsListener;
	private PrintCachesListener printCachesListener;
	private Vector<NewRequestListener> newReqListeners = new Vector<NewRequestListener>();
	
	
	public Controller(Vue v, Topcell t) {
		vue = v;
		topcell = t;
		
		nextCycleListener = new NextCycleListener(this);
		showProcsListener = new ShowProcsListener(this);
		printCachesListener = new PrintCachesListener(this);
		
		vue.setControlleur(this);
	}
	

	public NextCycleListener getNextCycleListener() {
		return nextCycleListener;
	}
	
	public ShowProcsListener getShowProcsListener() {
		return showProcsListener;
	}

	public PrintCachesListener getPrintCachesListener() {
		return printCachesListener;
	}
	

	public Topcell getTopcell() {
		return topcell;
	}
	

	public void repaint() {
		vue.repaint();
	}
	

	public Vue getVue() {
		return vue;
	}
	

	public ModulePosition buildModulePositionPCM() {
		ModulePosition res = new ModulePosition();
		int i = 0;
		for (Module m : getTopcell().getAllModules()) {
			// assert(!srcid2pos2.containsKey(m.getSrcid()));
			res.addModulePosition(m, i);
			i++;
		}
		return res;
	}
	

	public ModulePosition buildModulePositionCM() {
		ModulePosition res = new ModulePosition();
		int i = 0;
		for (Module m : getTopcell().getAllModules()) {
			if (!(m instanceof Processor)) {
				res.addModulePosition(m, i);
				i++;
			}
		}
		return res;
	}
	

	public void buildNewRequestListeners() {
		for (Module m : getTopcell().getAllModules()) {
			if (m instanceof Processor) {
				newReqListeners.add(new NewRequestListener(this, m.getSrcid()));
			}
		}
	}
	

	public NewRequestListener getNewRequestListener(int id) {
		for (NewRequestListener l : newReqListeners) {
			if (l.getSrcid() == id) {
				return l;
			}
		}
		return null;
	}
	
}
