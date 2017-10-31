package controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 
 * @author QLM
 * This class implements the listener associated to the "Show Procs" button.
 */
public class ShowProcsListener implements ActionListener {

	private Controller controlleur;
	
	public ShowProcsListener(Controller c) {
		controlleur = c;
	}
	
	
	public void actionPerformed(ActionEvent e) {
		controlleur.getVue().switchShowProcs();
		controlleur.repaint();
	}
	
}
