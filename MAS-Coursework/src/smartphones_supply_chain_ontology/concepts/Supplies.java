package smartphones_supply_chain_ontology.concepts;

import jade.content.Concept;
import jade.content.onto.annotations.AggregateSlot;

import java.util.HashMap;

public class Supplies implements Concept{
	
	HashMap<Component,Integer> componentsQuantity;
	
	@AggregateSlot(cardMin = 1)
	public HashMap<Component,Integer> getComponentsQuantity(){
		return componentsQuantity;
	}
	
	public void setComponentsQuantity(HashMap<Component,Integer> componentsQuantity) {
		this.componentsQuantity = componentsQuantity;
	}

}
