package simulation;

import java.awt.Dimension;

import controller.Controller;
import topcells.*;
import view.Vue;

/**
 * Main class.
 * @author QLM
 *
 */
public class Simul {
	public static void main(String[] args) {
		Dimension dim = new Dimension(1000, 800);
		Topcell topcell = new Top2Procs1Ram();
		Vue vue = new Vue(dim);
		new Controller(vue, topcell);

		vue.setVisible(true);
		vue.pack();
	}
}
