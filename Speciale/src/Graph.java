import java.io.FileNotFoundException;
import java.util.ArrayList;

public class Graph {
	private ArrayList<Node> nodes;
	private ArrayList<Edge> edges;
	private Data data;
	
	public Graph() throws FileNotFoundException {
		data = new Data("Demand_Baltic.csv", "fleet_Baltic.csv");
		Result.initialize(this);
		this.nodes = new ArrayList<Node>();
		this.edges = new ArrayList<Edge>();
		createCentroids();
		createOmissionEdges();
	}
	
	private void createCentroids(){
		//Sets the number of centroids in the Node class once and for all, and is then garbage collected.
		new Node(data.getPorts().size());
		for(Port i : data.getPorts().values()){
			Node newCentroid = new Node(i);
			nodes.add(newCentroid);
			i.setCentroidNode(newCentroid);
		}
	}
	
	private void createOmissionEdges(){
		for(Demand i : data.getDemands()){
			Node fromCentroid = i.getOrigin().getCentroidNode();
			Node toCentroid = i.getDestination().getCentroidNode();
			Edge newOmissionEdge = new Edge(fromCentroid, toCentroid, i.getRate());
			edges.add(newOmissionEdge);
		}
	}
	
	public Rotation createRotation(ArrayList<DistanceElement> distances, VesselClass vesselClass){
		Rotation rotation = new Rotation(vesselClass);
		createRotationEdges(distances, rotation, vesselClass);
		createLoadUnloadEdges(rotation);
//		createTransshipmentEdges(Rotation rotation);
		return rotation;
	}
	
	private void createRotationEdges(ArrayList<DistanceElement> distances, Rotation rotation, VesselClass vesselClass){
		checkDistances(distances);
		//Rotation opened at port 0 outside of for loop.
		DistanceElement currDist;
		Port firstPort = distances.get(0).getOrigin();
		Port depPort = firstPort;
		Port arrPort;
		Node firstNode = createRotationNode(depPort, rotation, true);
		Node depNode = firstNode;
		Node arrNode;
		for(int i = 0; i < distances.size()-1; i++){
			currDist = distances.get(i);
			arrPort = currDist.getDestination();
			arrNode = createRotationNode(arrPort, rotation, false);
			createRotationEdge(rotation, depNode, arrNode, 0, vesselClass.getCapacity(), i, currDist);
			depPort = arrPort;
			depNode = createRotationNode(depPort, rotation, true);
			createRotationEdge(rotation, arrNode, depNode, 0, vesselClass.getCapacity(), -1, null);
		}
		//Rotation closed at port 0 outside of for loop.
		currDist = distances.get(distances.size()-1);
		arrPort = currDist.getDestination();
		arrNode  = createRotationNode(arrPort, rotation, false);
		createRotationEdge(rotation, depNode, arrNode, 0, vesselClass.getCapacity(), distances.size()-1, currDist);
		createRotationEdge(rotation, arrNode, firstNode, 0, vesselClass.getCapacity(), -1, null);
	}
	
	private Node createRotationNode(Port port, Rotation rotation, boolean departure){
		Node newNode = new Node(port, rotation, departure);
		rotation.addRotationNode(newNode);
		nodes.add(newNode);
		return newNode;
	}

	private void createRotationEdge(Rotation rotation, Node fromNode, Node toNode, int cost, int capacity, int noInRotation, DistanceElement distance){
		Edge newEdge = new Edge(fromNode, toNode, cost, capacity, true, rotation, noInRotation, distance);
		rotation.addRotationEdge(newEdge);
		edges.add(newEdge);
	}
	
	private void checkDistances(ArrayList<DistanceElement> distances){
		Port firstPort = distances.get(0).getOrigin();
		for(int i = 1; i < distances.size(); i++){
			Port portA = distances.get(i-1).getDestination();
			Port portB = distances.get(i).getOrigin();
			if(portA.getPortId() != portB.getPortId()){
				throw new RuntimeException("The distances are not compatible.");
			}
		}
		Port lastPort = distances.get(distances.size()-1).getDestination();
		if(firstPort.getPortId() != lastPort.getPortId()){
			throw new RuntimeException("The rotation is not closed.");
		}
	}
	
	private void createLoadUnloadEdges(Rotation rotation){
		for(Node i : rotation.getRotationNodes()){
			if(i.isArrival()){
				createLoadUnloadEdge(i, i.getPort().getCentroidNode());
			} else if(i.isDeparture()){
				createLoadUnloadEdge(i.getPort().getCentroidNode(), i);
			} else {
				throw new RuntimeException("Tried to create load/unload edge that does not match definition.");
			}
		}
	}
	
	private void createLoadUnloadEdge(Node fromNode, Node toNode){
		int loadUnloadCost = fromNode.getPort().getMoveCost();
		Edge newEdge = new Edge(fromNode, toNode, loadUnloadCost, Integer.MAX_VALUE, false, null, -1, null);
		edges.add(newEdge);
	}

	public ArrayList<Node> getNodes() {
		return nodes;
	}

	public ArrayList<Edge> getEdges() {
		return edges;
	}

	public Data getData() {
		return data;
	}
}
