package smartphones_supply_chain_ontology.concepts;

import jade.content.Concept;
import jade.content.onto.annotations.AggregateSlot;
import jade.content.onto.annotations.Slot;

import java.util.ArrayList;
import java.util.HashMap;

public class Supplies implements Concept{
	
	private ArrayList<Component> components;
	private int componentsQuantity;
	
	@AggregateSlot(cardMin = 1)
	public ArrayList<Component> getComponents(){
		return components;
	}
	
	public void setComponents(ArrayList<Component> components) {
		this.components = components;
	}
	
	@Slot(mandatory = true)
	public int getComponentsQuantity(){
		return componentsQuantity;
	}
	
	public void setComponentsQuantity(int componentsQuantity) {
		this.componentsQuantity = componentsQuantity;
	}

}
