package controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * This class implements the listener for the "Next Cycle" button
 * @author QLM
 *
 */
class NextCycleListener implements ActionListener {

	private Controller controlleur;
	
	public NextCycleListener(Controller c) {
		controlleur = c;
	}
	
	
	public void actionPerformed(ActionEvent e) {
		controlleur.getTopcell().simulate1Cycle();
		controlleur.repaint();
		controlleur.getVue().getPanneau().setScrollBarMax();
	}

}
