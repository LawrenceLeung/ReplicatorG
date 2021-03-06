/*
  MachineModel.java

  A class to model a 3-axis machine.

  Part of the ReplicatorG project - http://www.replicat.org
  Copyright (c) 2008 Zach Smith

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package replicatorg.machine.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import replicatorg.app.Base;
import replicatorg.app.tools.XML;
import replicatorg.util.Point5d;

public class MachineModel
{
	//our xml config info
	protected Node xml = null;
	
	//our machine space
	//private Point3d currentPosition;
	@SuppressWarnings("unused")
	private Point5d minimum;
	private Point5d maximum;
	private EnumMap<AxisId, Endstops> endstops = new EnumMap<AxisId, Endstops>(AxisId.class);

	// Which axes exist on this machine
	private Set<AxisId> axes = EnumSet.noneOf(AxisId.class);
	
	//feedrate information
	private Point5d maximumFeedrates;
	private Point5d stepsPerMM;
	
	//our drive status
	protected boolean drivesEnabled = true;
	protected int gearRatio = 0;
	
	//our tool models
	protected Vector<ToolModel> tools;
	protected final AtomicReference<ToolModel> currentTool = new AtomicReference<ToolModel>();
	protected final ToolModel nullTool = new ToolModel();

	//our clamp models	
	protected Vector<ClampModel> clamps;
	
	// our build volume
	protected BuildVolume buildVolume;

	/*************************************
	*  Creates the model object.
	*************************************/
	public MachineModel()
	{
		clamps = new Vector<ClampModel>();
		tools = new Vector<ToolModel>();
		buildVolume = new BuildVolume(100,100,100); // preload it with the default values
		
		//currentPosition = new Point3d();
		minimum = new Point5d();
		maximum = new Point5d();
		maximumFeedrates = new Point5d();
		stepsPerMM = new Point5d(1, 1, 1, 1, 1); //use ones, because we divide by this!
		
		currentTool.set(nullTool);
	}
	
	//load data from xml config
	public void loadXML(Node node)
	{
		xml = node;
		
		parseAxes();
		parseClamps();
		parseTools();
		parseBuildVolume();
		
	}
	
	//load axes configuration
	private void parseAxes()
	{
		if(XML.hasChildNode(xml, "geometry"))
		{
			Node geometry = XML.getChildNodeByName(xml, "geometry");
			
			//look through the axes.
			NodeList axisNodes = geometry.getChildNodes();
			for (int i=0; i<axisNodes.getLength(); i++)
			{
				Node axis = axisNodes.item(i);
				
				if (axis.getNodeName().equals("axis"))
				{
					//parse our information.
					String idStr = XML.getAttributeValue(axis, "id");
					try {
						AxisId id = AxisId.valueOf(idStr.toUpperCase());
						axes.add(id);
						//initialize values
						double length = 0.0;
						double maxFeedrate = 0.0;
						double stepspermm = 1.0;
						Endstops endstops = Endstops.NONE;
						//if values are missing, ignore them.
						try {
						 	length = Double.parseDouble(XML.getAttributeValue(axis, "length"));
						} catch (Exception e) {}
						try {
						 	maxFeedrate = Double.parseDouble(XML.getAttributeValue(axis, "maxfeedrate"));
						} catch (Exception e) {}
						try {
						 	String spmm = XML.getAttributeValue(axis, "stepspermm");
						 	if (spmm == null) spmm = XML.getAttributeValue(axis, "scale"); // Backwards compatibility
						 	stepspermm = Double.parseDouble(spmm);
						} catch (Exception e) {}
						String endstopStr = XML.getAttributeValue(axis, "endstops");
						if (endstopStr != null) {
							try {
								endstops = Endstops.valueOf(endstopStr.toUpperCase());
							} catch (IllegalArgumentException iae) {
								Base.logger.severe("Unrecognized endstop value "+endstopStr+" for axis "+id.name());
							}
						}
						maximum.setAxis(id,length);
						maximumFeedrates.setAxis(id,maxFeedrate);
						stepsPerMM.setAxis(id,stepspermm);
						this.endstops.put(id, endstops);
						Base.logger.fine("Loaded axis " + id.name() + ": (Length: " + 
								length + "mm, max feedrate: " + maxFeedrate + " mm/min, scale: " + 
								stepspermm + " steps/mm)");
					} catch (IllegalArgumentException iae) {
						// Unrecognized axis!
						Base.logger.severe("Unrecognized axis "+idStr+" found in machine descriptor!");
					}

				}
			}
		}
	}
	
	//load clamp configuration
	private void parseClamps()
	{
		if(XML.hasChildNode(xml, "clamps"))
		{
			Node clampsNode = XML.getChildNodeByName(xml, "clamps");
			
			//look through the axes.
			NodeList clampKids = clampsNode.getChildNodes();
			for (int i=0; i<clampKids.getLength(); i++)
			{
				Node clampNode = clampKids.item(i);
				
				ClampModel clamp = new ClampModel(clampNode);
				clamps.add(clamp);
				
				System.out.println("adding clamp #" + clamps.size());
			}
		}
	}
	
	//load tool configuration
	private void parseTools()
	{
		if(XML.hasChildNode(xml, "tools"))
		{
			Node toolsNode = XML.getChildNodeByName(xml, "tools");
			
			//look through the axes.
			NodeList toolKids = toolsNode.getChildNodes();
			for (int i=0; i<toolKids.getLength(); i++)
			{
				Node toolNode = toolKids.item(i);
				
				if (toolNode.getNodeName().equals("tool"))
				{
					ToolModel tool = new ToolModel(toolNode);
					if (tool.getIndex() == -1) {
						tool.setIndex(tools.size());
						tools.add(tool);
					} else {
						if (tools.size() <= tool.getIndex()) {
							tools.setSize(tool.getIndex()+1);
						}
						tools.set(tool.getIndex(), tool);
					}
					synchronized(currentTool)
					{
						if (currentTool.get() == nullTool) {
							this.selectTool(tool.getIndex());
						}
					}
				}
			}
		}
	}
	//load axes configuration
	private void parseBuildVolume()
	{
//		Base.logger.info("parsing build volume!");
		
		if(XML.hasChildNode(xml, "geometry"))
		{
			Node geometry = XML.getChildNodeByName(xml, "geometry");
			
			//look through the axes.
			NodeList axes = geometry.getChildNodes();
			for (int i=0; i<axes.getLength(); i++)
			{
				Node axis = axes.item(i);
				
				if (axis.getNodeName().equals("axis"))
				{
					//parse our information.
					String id = XML.getAttributeValue(axis, "id");

					//initialize values
				 	double length = 100; // 100mm by default
					
					//if values are missing, ignore them.
					try {
					 	length = Double.parseDouble(XML.getAttributeValue(axis, "length"));
					} catch (Exception e) {}
					
					//create the right variables.
					if (id.toLowerCase().equals("x"))
					{
						buildVolume.setX((int)length);
					}
					else if (id.toLowerCase().equals("y"))
					{
						buildVolume.setY((int)length);
					}
					else if (id.toLowerCase().equals("z"))
					{
						buildVolume.setZ((int)length);
					}
				}
			}
		}
		
	}

	/*************************************
	*  Reporting available axes
	*************************************/
	
	/** Return a set enumerating all the axes that this machine has available.
	 */
	public Set<AxisId> getAvailableAxes() { return axes; }
	/** Report whether this machine has the specified axis.
	 * @param id The axis to check
	 * @return true if the axis is available, false otherwise
	 */
	public boolean hasAxis(AxisId id) { return axes.contains(id); }
	
	/*************************************
	*  Convert steps to millimeter units
	*************************************/

	public Point5d stepsToMM(Point5d steps)
	{
		Point5d temp = new Point5d();
		temp.div(steps, stepsPerMM);
		
		return temp;
	}
	
	/*************************************
	*  Convert millimeters to machine steps
	*************************************/

	public Point5d mmToSteps(Point5d mm)
	{
		Point5d temp = new Point5d();
		temp.mul(mm,stepsPerMM);
		temp.round(); // integer step counts please
		
		return temp;
	}

	/*************************************
	* Drive interface functions
	*************************************/
	public void enableDrives()
	{
		drivesEnabled = true;
	}
	
	public void disableDrives()
	{
		drivesEnabled = false;
	}
	
	public boolean areDrivesEnabled()
	{
		return drivesEnabled;
	}
	
	/*************************************
	* Gear Ratio functions
	*************************************/
	public void changeGearRatio(int ratioIndex)
	{
		gearRatio = ratioIndex;
	}
	
	/*************************************
	* Clamp interface functions
	*************************************/
	public ClampModel getClamp(int index)
	{
		try {
			ClampModel c = (ClampModel)clamps.get(index);
			return c;
		} catch (ArrayIndexOutOfBoundsException e) {
			Base.logger.severe("Cannot get non-existant clamp (#" + index + ".");
			e.printStackTrace();
		}
		
		return null;
	}
	
	/*************************************
	*  Tool interface functions
	*************************************/
	public void selectTool(int index)
	{
		synchronized(currentTool)
		{
			try {
				currentTool.set( (ToolModel)tools.get(index) );
				if (currentTool.get() == null) { 
					Base.logger.severe("Cannot select non-existant tool (#" + index + ").");
					currentTool.set(nullTool);
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				if (xml != null) { 
					Base.logger.severe("Cannot select non-existant tool (#" + index + ").");
				} else {
					// If this machine is not configured, it's presumed it's a null machine
					// and it's expected that toolheads are not specified.
				}
				currentTool.set(nullTool);
			}
		}
	}

	public ToolModel currentTool()
	{
		return currentTool.get();
	}
	
	public ToolModel getTool(int index)
	{
		try {
			return tools.get(index);
		} catch (ArrayIndexOutOfBoundsException e) {
			Base.logger.severe("Cannot get non-existant tool (#" + index + ".");
			e.printStackTrace();
		}
		
		return null;
	}
	public BuildVolume getBuildVolume()
	{
		return buildVolume;
	}
	
	public Vector<ToolModel> getTools()
	{
		return tools;
	}

	public void addTool(ToolModel t)
	{
		tools.add(t);
	}
	
	
	public void setTool(int index, ToolModel t)
	{
		try {
			tools.set(index, t);
		} catch (ArrayIndexOutOfBoundsException e) {
			Base.logger.severe("Cannot set non-existant tool (#" + index + ".");
			e.printStackTrace();
		}
	}

  public Point5d getMaximumFeedrates() {
    return maximumFeedrates;
  }
  
  /** returns the endstop configuration for the givin axis */
  public Endstops getEndstops(AxisId axis)
  {
	  return this.endstops.get(axis);
  }

}
