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
import smartphones_supply_chain_ontology.predicates.Costs;
import smartphones_supply_chain_ontology.predicates.NewDay;
import smartphones_supply_chain_ontology.predicates.NoMoreOrdersToday;

public class ManufacturerAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	private AID[] customersAID;
	private AID warehouseAID;
	private AID dayCoordinatorAID;
	private int dailyProfit;
	private int totalProfit;
	private int minimumBenefitMargin;
	private ArrayList<Order> dailyOrdersReady;
	
	// Initialize the agent
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
		// Get the minimum benefit from the arguments
		Object[] args = this.getArguments();
		minimumBenefitMargin = (int) args[0];
		// Initialize variables
		totalProfit = 0;
		dailyOrdersReady = new ArrayList<Order>();
		// Wait for other agents to initialize
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindCustomersBehaviour());
		this.addBehaviour(new FindWarehouseBehaviour());
		this.addBehaviour(new FindDayCoordinatorBehaviour());
		this.addBehaviour(new DayCoordinatorWaiterBehaviour());
	}
	
	// Called when agent is deleted
	protected void TakeDown() {
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
					ContentElement ce = null;
					ce = getContentManager().extractContent(msg);
					// Every new day the manufacturer is going to carry a series of operations
					if(ce instanceof NewDay) {
						// Add sequential behaviour
						SequentialBehaviour dailyActivity = new SequentialBehaviour();
						dailyActivity.addSubBehaviour(new ProcessOrderBehaviour());
						dailyActivity.addSubBehaviour(new ReceiveOrdersReadyBehaviour());
						dailyActivity.addSubBehaviour(new SendOrdersBehaviour());
						dailyActivity.addSubBehaviour(new CalculateDailyProfitBehaviour());
						dailyActivity.addSubBehaviour(new EndDayBehaviour());
					} else {
						// If end simulation is received print total profit
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
	
	// Behaviour that is going to agree or refuse to process an order from a manufacturer
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
									// Transform java objects to strings
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
						if(ce instanceof Costs) {
							Costs costs = (Costs) ce;
							// Extact benefit from the costs and the sell price
							int sellPrice = currentOrder.getUnitPrice() * currentOrder.getQuantity();
							int benefit = sellPrice - costs.getCost();
							// Decide whether to accept the order or not based on the minimum benefit margin
							int benefitMargin = (benefit / sellPrice) * 100;
							if(benefitMargin >= minimumBenefitMargin) {
								// Add costs of the order to the daily profit
								dailyProfit -= costs.getCost();
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
			// Send notification to warehouse about not more orders for the
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
	
	// Behaviour that is going to wait to receive orders from the warehouse
	private class ReceiveOrdersReadyBehaviour extends Behaviour{
		public void action() {
			
		}
		
		public boolean done() {
			return false;
		}
	}
	
	// Behaviour that is going to send any orders ready to the customer and receive payment from them
	private class SendOrdersBehaviour extends Behaviour{
		public void action() {
			
		}
		public boolean done() {
			return false;
		}
	}
	
	// Behaviour that is going to calculate the daily profit
	private class CalculateDailyProfitBehaviour extends Behaviour{
		public void action() {
			
		}
		public boolean done() {
			return false;
		}
	}
	
	// Behaviour that is going to call the day off
	private class EndDayBehaviour extends OneShotBehaviour{
		public void action() {
			// Reset daily profit and ready orders
		}
	}
	

}
