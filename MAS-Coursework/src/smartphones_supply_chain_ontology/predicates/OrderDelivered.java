package smartphones_supply_chain_ontology.predicates;

import jade.content.Predicate;
import jade.content.onto.annotations.Slot;
import smartphones_supply_chain_ontology.concepts.Order;

public class OrderDelivered implements Predicate{

	private Order order;
	
	@Slot(mandatory = true)
	public Order getOrder() {
		return order;
	}
	
	public void setOrder(Order order) {
		this.order = order;
	}
}
