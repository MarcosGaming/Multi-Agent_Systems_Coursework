package smartphones_supply_chain;

import java.util.ArrayList;


import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import smartphones_supply_chain_ontology.SupplyChainOntology;
import smartphones_supply_chain_ontology.actions.CalculateOrderCost;
import smartphones_supply_chain_ontology.actions.PrepareOrderToAssemble;
import smartphones_supply_chain_ontology.actions.SellOrder;
import smartphones_supply_chain_ontology.concepts.Order;
import smartphones_supply_chain_ontology.predicates.DayEnd;
import smartphones_supply_chain_ontology.predicates.NewDay;
import smartphones_supply_chain_ontology.predicates.NoMoreOrdersToday;
import smartphones_supply_chain_ontology.predicates.OrderDelivered;
import smartphones_supply_chain_ontology.predicates.OrdersReady;
import smartphones_supply_chain_ontology.predicates.Payment;
import smartphones_supply_chain_ontology.predicates.PredictedOrderCost;
import smartphones_supply_chain_ontology.predicates.WarehouseExpenses;
import smartphones_supply_chain_ontology.predicates.WarehouseExpensesToday;

public class ManufacturerAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	
	private AID[] customersAID;
	private AID warehouseAID;
	private AID dayCoordinatorAID;
	
	private int dailyProfit;
	private int dailyPurchasesCost;
	private int dailyPenaltiesCost;
	private int dailyWarehouseStorageCost;
	private int dailyPayments;
	
	private int totalProfit;
	private static final int minimumBenefitMargin = 3;
	
	// Initialise the agent
	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		// Register agent into the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Manufacturer");
		sd.setName(getLocalName() + "-manufacturer-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
		// Initialise variables
		totalProfit = 0;
		dailyProfit = 0;
		dailyPurchasesCost = 0;
		dailyPenaltiesCost = 0;
		dailyWarehouseStorageCost = 0;
		dailyPayments = 0;
		// Wait for other agents to initialise
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindCustomersBehaviour());
		this.addBehaviour(new FindWarehouseBehaviour());
		this.addBehaviour(new FindDayCoordinatorBehaviour());
		this.addBehaviour(new DayCoordinatorWaiterBehaviour());
	}
	
	// Called when agent is deleted
	protected void takeDown() {
		// Bye message
		System.out.println("Agent " + this.getLocalName() + " is terminating.");
		// Deregister agent from the yellow pages
		try {
			DFService.deregister(this);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
	}
	
	// Behaviour to find the customer agents in the yellow pages
	private class FindCustomersBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription customerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Customer");
			customerTemplate.addServices(sd);
			try {
				DFAgentDescription[] customerAgents = DFService.search(myAgent, customerTemplate);
				int size = customerAgents.length;
				customersAID = new AID[size];
				for(int i = 0; i < size; i++) {
					customersAID[i] = customerAgents[i].getName();
				}
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour to find the warehouse agent in the yellow pages
	private class FindWarehouseBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription warehouseTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Warehouse");
			warehouseTemplate.addServices(sd);
			try {
				warehouseAID = new AID();
				DFAgentDescription[] warehouseAgents = DFService.search(myAgent, warehouseTemplate);
				warehouseAID = warehouseAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour to find the dayCoordinator agent in the yellow pages
	private class FindDayCoordinatorBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription dayCoordinatorTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("DayCoordinator");
			dayCoordinatorTemplate.addServices(sd);
			try {
				dayCoordinatorAID = new AID();
				DFAgentDescription[] dayCoordinatorAgents = DFService.search(myAgent, dayCoordinatorTemplate);
				dayCoordinatorAID = dayCoordinatorAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour that waits for new day or end simulation calls
	public class DayCoordinatorWaiterBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchSender(dayCoordinatorAID));
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					// Every new day the manufacturer is going to carry out a series of operations
					if(ce instanceof NewDay) {
						// Add sequential behaviour
						SequentialBehaviour dailyActivity = new SequentialBehaviour();
						dailyActivity.addSubBehaviour(new ProcessOrderBehaviour());
						dailyActivity.addSubBehaviour(new ProcessOrdersReadyBehaviour());
						dailyActivity.addSubBehaviour(new CalculateDailyProfitBehaviour());
						dailyActivity.addSubBehaviour(new EndDayBehaviour());
						myAgent.addBehaviour(dailyActivity);
					} else {
						myAgent.doDelete();
					}
					
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			} else {
				block();
			}
		}
	}
	
	// Behaviour that is going to agree or refuse to process an order from a customer
	private class ProcessOrderBehaviour extends Behaviour{
		int step = 0;
		int ordersReceived = 0;
		Order currentOrder;
		
		public void action() {
			switch(step) {
			// Receive a sell order request message from a customer
			case 0:
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
				ACLMessage msg = myAgent.receive(mt);
				if(msg != null) {
					try {
						ContentElement ce = getContentManager().extractContent(msg);
						if(ce instanceof Action) {
							Concept action = ((Action)ce).getAction();
							if(action instanceof SellOrder) {
								SellOrder sellOrder = (SellOrder) action;
								// Create agent action
								CalculateOrderCost calculateOrderCost = new CalculateOrderCost();
								calculateOrderCost.setOrder(sellOrder.getOrder());
								// Create wrapper
								Action request = new Action();
								request.setAction(calculateOrderCost);
								request.setActor(warehouseAID);
								// Request warehouse to calculate order cost
								ACLMessage costRequestMsg = new ACLMessage(ACLMessage.REQUEST);
								costRequestMsg.addReceiver(warehouseAID);
								costRequestMsg.setLanguage(codec.getName());
								costRequestMsg.setOntology(ontology.getName());
								try {
									// Convert java objects to strings
									getContentManager().fillContent(costRequestMsg, request);
									myAgent.send(costRequestMsg);
								} catch (CodecException codece) {
									codece.printStackTrace();
								} catch (OntologyException oe) {
									oe.printStackTrace();
								}
								currentOrder = sellOrder.getOrder();
								step++;
								ordersReceived++;
							}
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				}
				break;
			// Receive costs message from the warehouse
			case 1:
				MessageTemplate mt1 = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg1 = myAgent.receive(mt1);
				if( msg1 != null) {
					try {
						ContentElement ce = getContentManager().extractContent(msg1);
						if(ce instanceof PredictedOrderCost) {
							PredictedOrderCost costs = (PredictedOrderCost) ce;
							// Extact benefit from the costs and the sell price
							int sellPrice = currentOrder.getUnitPrice() * currentOrder.getQuantity();
							int benefit = sellPrice - costs.getCost();
							// Decide whether to accept the order or not based on the minimum benefit margin
							float benefitMargin = ((float)benefit / (float)sellPrice) * 100.0f;
							if(benefitMargin >= minimumBenefitMargin) {
								// Create action
								PrepareOrderToAssemble orderToAssemble = new PrepareOrderToAssemble();
								orderToAssemble.setOrder(currentOrder);
								// Create wraper
								Action request = new Action();
								request.setAction(orderToAssemble);
								request.setActor(warehouseAID);
								// Request warehouse to prepare the order to assemble
								ACLMessage prepareOrderRequestMsg = new ACLMessage(ACLMessage.REQUEST);
								prepareOrderRequestMsg.addReceiver(warehouseAID);
								prepareOrderRequestMsg.setLanguage(codec.getName());
								prepareOrderRequestMsg.setOntology(ontology.getName());
								try {
									// Transform java objects to strings
									getContentManager().fillContent(prepareOrderRequestMsg, request);
									myAgent.send(prepareOrderRequestMsg);
								} catch (CodecException codece) {
									codece.printStackTrace();
								} catch (OntologyException oe) {
									oe.printStackTrace();
								}
							}
							// Check if there are more orders to receive from customers
							if(ordersReceived == customersAID.length) {
								step++;
							} else {
								step = 0;
							}
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				}
				break;
			// Send notification to warehouse about no more orders for the day
			case 2:
				// Create predicate
				NoMoreOrdersToday  noMoreOrdersToday = new NoMoreOrdersToday();
				// Send message to warehouse
				ACLMessage noMoreOrdersInformMsg = new ACLMessage(ACLMessage.INFORM);
				noMoreOrdersInformMsg.addReceiver(warehouseAID);
				noMoreOrdersInformMsg.setLanguage(codec.getName());
				noMoreOrdersInformMsg.setOntology(ontology.getName());
				try {
					// Transform java objects to string
					getContentManager().fillContent(noMoreOrdersInformMsg, noMoreOrdersToday);
					myAgent.send(noMoreOrdersInformMsg);
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
				step++;
				break;
			}
		}
		
		public boolean done() {
			return step == 3;
		}
	}
	
	// Behaviour that is going to receive the orders ready from the warehouse, send them to the customers and receive the corresponding payment
	private class ProcessOrdersReadyBehaviour extends Behaviour{
		private int step = 0;
		private int numbOrdersReadyDelivered = 0;
		private int numbPaymentsReceived = 0;
		
		public void action() {
			switch(step) {
			// Receive orders ready from the warehouse
			case 0:
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg = myAgent.receive(mt);
				if(msg != null) {
					try {
						// Transform string to java objects
						ContentElement ce = getContentManager().extractContent(msg);
						if(ce instanceof OrdersReady) {
							OrdersReady ordersReady = (OrdersReady) ce;
							// If there are any orders ready send them to the according customer
							if(ordersReady.getOrders() == null || ordersReady.getOrders().isEmpty()) {
								step = 2;
							} 
							else
							{
								for(Order orderReady : ordersReady.getOrders()) {
									// Create predicate
									OrderDelivered orderDelivered = new OrderDelivered();
									orderDelivered.setOrder(orderReady);
									// Send order ready to customer
									ACLMessage orderDeliveredMsg = new ACLMessage(ACLMessage.INFORM);
									orderDeliveredMsg.addReceiver(orderReady.getAID());
									orderDeliveredMsg.setLanguage(codec.getName());
									orderDeliveredMsg.setOntology(ontology.getName());
									try {
										// Transform java objects to string
										getContentManager().fillContent(orderDeliveredMsg, orderDelivered);
										myAgent.send(orderDeliveredMsg);
									} catch (CodecException codece) {
										codece.printStackTrace();
									} catch (OntologyException oe) {
										oe.printStackTrace();
									}
								}
								numbOrdersReadyDelivered = ordersReady.getOrders().size();
								step = 1;
							}
						} else {
							myAgent.postMessage(msg);
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				} else {
					block();
				}
				break;
			// Receive payment from customers
			case 1:
				MessageTemplate mt1 = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg1 = myAgent.receive(mt1);
				if(msg1 != null) {
					try {
						// Transform strings to java objects
						ContentElement ce = getContentManager().extractContent(msg1);
						if(ce instanceof Payment) {
							// Add payment to daily payments
							Payment payment = (Payment) ce;
							dailyPayments += payment.getAmount();
							numbPaymentsReceived++;
							// When all payments are received increase step
							if(numbOrdersReadyDelivered == numbPaymentsReceived) {
								step++;
							}
						}
					}  catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				} else {
					block();
				}
				break;
			}
		}
		
		public boolean done() {
			return step == 2;
		}
	}
	
	// Behaviour that is going to finish the calculation of the daily profit and add it to the total profit
	private class CalculateDailyProfitBehaviour extends Behaviour{
		private int step = 0;
		
		public void action() {
			switch(step) {
			// Query the warehouse about all the costs of the day
			case 0:
				// Create predicate
				WarehouseExpensesToday warehouseExpensesToday = new WarehouseExpensesToday();
				// Send message
				ACLMessage msg = new ACLMessage(ACLMessage.QUERY_IF);
				msg.addReceiver(warehouseAID);
				msg.setLanguage(codec.getName());
				msg.setOntology(ontology.getName());
				try {
					// Transform java objects to strings
					getContentManager().fillContent(msg, warehouseExpensesToday);
					myAgent.send(msg);
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
				step++;
				break;
			// Receive warehouse costs message from warehouse
			case 1:
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg1 = myAgent.receive(mt);
				if(msg1 != null) {
					try {
						// Transform strings to java objects
						ContentElement ce = getContentManager().extractContent(msg1);
						if(ce instanceof WarehouseExpenses) {
							// Get storage cost for the day
							WarehouseExpenses expenses = (WarehouseExpenses) ce;
							dailyPurchasesCost = expenses.getSuppliesCost();
							dailyPenaltiesCost = expenses.getPenaltiesCost();
							dailyWarehouseStorageCost = expenses.getStorageCost();
							step++;
						}
					} catch (CodecException codece) {
						codece.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				}
				break;
			// Calculate daily profit
			case 2:
				dailyProfit = dailyPayments - dailyPenaltiesCost - dailyWarehouseStorageCost - dailyPurchasesCost;
				totalProfit += dailyProfit;
				System.out.println("Daily profit of: " + dailyProfit);
				System.out.println("Total profit of: " + totalProfit);
				step++;
				break;
			}
		}
		
		public boolean done() {
			return step == 3;
		}
	}
	
	// Behaviour that is going to call the day off
	private class EndDayBehaviour extends OneShotBehaviour{
		public void action() {
			// Reset daily variables
			dailyProfit = 0;
			dailyPurchasesCost = 0;
			dailyPenaltiesCost = 0;
			dailyWarehouseStorageCost = 0;
			dailyPayments = 0;
			// Create predicate
			DayEnd dayEnd = new DayEnd();
			// Send day end message to day coordinator
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(dayCoordinatorAID);
			msg.setLanguage(codec.getName());
			msg.setOntology(ontology.getName());
			try {
				// Transform java objects to strings
				getContentManager().fillContent(msg, dayEnd);
				myAgent.send(msg);
			} catch (CodecException codece) {
				codece.printStackTrace();
			} catch (OntologyException oe) {
				oe.printStackTrace();
			}
		}
	}
	

}
