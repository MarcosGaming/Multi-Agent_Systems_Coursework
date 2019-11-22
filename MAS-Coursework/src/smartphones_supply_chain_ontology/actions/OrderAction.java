package smartphones_supply_chain_ontology.actions;

import jade.content.AgentAction;
import jade.content.onto.annotations.Slot;
import smartphones_supply_chain_ontology.concepts.Order;

public class OrderAction implements AgentAction{
	
	private Order order;
	
	@Slot(mandatory = true)
	public Order getOrder() {
		return order;
	}
	
	public void setOrder(Order order) {
		this.order = order;
	}

}
