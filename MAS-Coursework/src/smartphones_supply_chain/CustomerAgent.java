package smartphones_supply_chain;

import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import smartphones_supply_chain_ontology.SupplyChainOntology;
import smartphones_supply_chain_ontology.actions.SellOrder;
import smartphones_supply_chain_ontology.concepts.Order;
import smartphones_supply_chain_ontology.concepts.PhabletSmartphone;
import smartphones_supply_chain_ontology.concepts.Ram;
import smartphones_supply_chain_ontology.concepts.SmallSmartphone;
import smartphones_supply_chain_ontology.concepts.Smartphone;
import smartphones_supply_chain_ontology.concepts.Storage;
import smartphones_supply_chain_ontology.predicates.NewDay;
import smartphones_supply_chain_ontology.predicates.OrderDelivered;
import smartphones_supply_chain_ontology.predicates.Payment;

public class CustomerAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	
	private AID manufacturerAID;
	private AID dayCoordinatorAID;
	
	// Initialize the agent
	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		// Register agent into the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Customer");
		sd.setName(getLocalName() + "-customer-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
		// Wait for the other agents to initialise
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindManufacturerBehaviour());
		this.addBehaviour(new FindDayCoordinatorBehaviour());
		this.addBehaviour(new DayCoordinatorWaiterBehaviour());
		this.addBehaviour(new ReceiveOrderBehaviour());
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
	
	// Behaviour to find the manufacturer agent in the yellow pages
	private class FindManufacturerBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription manufacturerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Manufacturer");
			manufacturerTemplate.addServices(sd);
			try {
				manufacturerAID = new AID();
				DFAgentDescription[] manufacturerAgents = DFService.search(myAgent, manufacturerTemplate);
				manufacturerAID = manufacturerAgents[0].getName();
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
					// Every new day the customer is going to request a single order to the manufacturer
					if(ce instanceof NewDay) {
						// Add to the customer the request order behaviour
						myAgent.addBehaviour(new RequestOrderBehaviour());
					} else {
						// If the predicate is not NewDay then it is EndSimulation
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
	
	// Behaviour to request an order to the manufacturer
	public class RequestOrderBehaviour extends OneShotBehaviour{
		public void action() {
			// Create new order
			Order order = createOrder(myAgent.getAID());
			// Create agent action
			SellOrder sellOrder = new SellOrder();
			sellOrder.setOrder(order);
			// Create wrapper
			Action request = new Action();
			request.setAction(sellOrder);
			request.setActor(manufacturerAID);
			// Send request to manufacturer
			ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
			msg.addReceiver(manufacturerAID);
			msg.setLanguage(codec.getName());
			msg.setOntology(ontology.getName());
			try {
				// Convert java object to string
				getContentManager().fillContent(msg, request);
				myAgent.send(msg);
			} catch (CodecException ce) {
				 ce.printStackTrace();
			} catch (OntologyException oe) {
				 oe.printStackTrace();
			} 
		}
		
	}
	
	// Behaviour that is going to handle orders being received
	public class ReceiveOrderBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchSender(manufacturerAID));
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				// Receive order
				try {
					// Convert string to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					if(ce instanceof OrderDelivered) {
						OrderDelivered orderDelivered = (OrderDelivered) ce;
						// Create predicate
						Payment payment = new Payment();
						payment.setAmount(orderDelivered.getOrder().getQuantity() * orderDelivered.getOrder().getUnitPrice());
						// Send payment
						ACLMessage paymentMsg = new ACLMessage(ACLMessage.INFORM);
						paymentMsg.addReceiver(msg.getSender());
						paymentMsg.setLanguage(codec.getName());
						paymentMsg.setOntology(ontology.getName());
						try {
							// Convert java objects to strings
							getContentManager().fillContent(paymentMsg, payment);
							myAgent.send(paymentMsg);
						} catch (CodecException codece) {
							codece.printStackTrace();
						} catch (OntologyException oe) {
							oe.printStackTrace();
						}
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
	
	// Method to create orders
	private Order createOrder(AID myAgent) {
		// Create smartphone specification
		Smartphone smartphone;
		// Small or Phablet smartphone decision
		if(Math.random() < 0.5){
			smartphone = new SmallSmartphone();
		} else {
			smartphone = new PhabletSmartphone();
		}
		// Ram adecision
		Ram ram = new Ram();
		if(Math.random() < 0.5) {
			ram.setAmount(4);
		 }else {
			ram.setAmount(8);
		}
		smartphone.setRam(ram);
		// Storage decision
		Storage storage = new Storage();
		if(Math.random() < 0.5) {
			storage.setCapacity(64);
		}else {
			storage.setCapacity(256);		
		}
		smartphone.setStorage(storage);
		// Create a new order
		Order order = new Order();
		order.setAID(myAgent);
		order.setSmartphone(smartphone);
		order.setQuantity((int)Math.floor(1 + 50 * Math.random()));
		order.setUnitPrice((int)Math.floor(100 + 500 * Math.random()));
		order.setDueDate((int)Math.floor(1 + 10 * Math.random()));
		order.setPenalty(order.getQuantity() * (int)Math.floor(1 + 50 * Math.random()));
		return order;
	}
	
}
