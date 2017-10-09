package controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import model.L1Controller;
import model.Module;


public class PrintCachesListener implements ActionListener {

	private Controller controlleur;
	
	public PrintCachesListener(Controller c) {
		controlleur = c;
	}
	
	
	public void actionPerformed(ActionEvent e) {
		for (Module module : controlleur.getTopcell().getAllModules()) {
			if (module instanceof L1Controller) {
				L1Controller l1controller = (L1Controller) module;
				l1controller.printContent();
			}
		}
	}
	
}
