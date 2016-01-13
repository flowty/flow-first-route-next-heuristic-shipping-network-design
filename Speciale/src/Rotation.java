import java.util.ArrayList;

public class Rotation {
	private VesselClass vesselClass;
	private ArrayList<Node> rotationNodes;
	private ArrayList<Edge> rotationEdges;
	private double rotationTime;
	private int noVessels;
	
	public Rotation(){
	}

	public Rotation(VesselClass vesselClass) {
		super();
		this.vesselClass = vesselClass;
		this.rotationNodes = new ArrayList<Node>();
		this.rotationEdges = new ArrayList<Edge>();
		this.rotationTime = 0;
		this.noVessels = 0;
	}
	
	public double calculateRotationTime(){
		double rotationTime = 0;
		for(Edge i : rotationEdges){
			rotationTime += i.getTravelTime();
		}
		return rotationTime;
	}
	
	public int calculateNoVessels(){
		//168 hours per week.
		return 1 + (int) rotationTime / 168;
	}

	public VesselClass getVesselClass() {
		return vesselClass;
	}
	
	public void addRotationNode(Node node){
		rotationNodes.add(node);
	}
	
	public void addRotationEdge(Edge edge){
		rotationEdges.add(edge);
	}

	public ArrayList<Node> getRotationNodes() {
		return rotationNodes;
	}

	public ArrayList<Edge> getRotationEdges() {
		return rotationEdges;
	}

	public int getNoVessels() {
		return noVessels;
	}

	@Override
	public String toString() {
		String print = "Rotation [vesselClass=" + vesselClass.getName() + ", noVessels=" + noVessels + "]\n";
		int counter = 0;
		for(Node i : rotationNodes){
			if(i.isDeparture()){
				print += "Port no. " + counter + ": " + i.getPort() + "\n";
				counter++;
			}
		}
		return print;
	}
	
}
