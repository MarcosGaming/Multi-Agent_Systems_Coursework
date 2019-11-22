package smartphones_supply_chain_ontology.predicates;

import java.util.List;

import jade.content.Predicate;
import jade.content.onto.annotations.AggregateSlot;
import smartphones_supply_chain_ontology.concepts.Order;

public class OrdersReady implements Predicate{

	private List<Order> orders;
	
	public List<Order> getOrders(){
		return orders;
	}
	
	public void setOrders(List<Order> orders) {
		this.orders = orders;
	}
}
