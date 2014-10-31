/*******************************************************************************
 * Copyright (c) 2010 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.nebula.visualization.xygraph.figures;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.SWTGraphics;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.nebula.visualization.internal.xygraph.undo.OperationsManager;
import org.eclipse.nebula.visualization.internal.xygraph.undo.ZoomCommand;
import org.eclipse.nebula.visualization.xygraph.linearscale.AbstractScale.LabelSide;
import org.eclipse.nebula.visualization.xygraph.linearscale.Range;
import org.eclipse.nebula.visualization.xygraph.util.GraphicsUtil;
import org.eclipse.nebula.visualization.xygraph.util.Log10;
import org.eclipse.nebula.visualization.xygraph.util.SingleSourceHelper2;
import org.eclipse.nebula.visualization.xygraph.util.XYGraphMediaFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/**
 * This class is the main figure for the plotting. It contains a PlotArea, which
 * contains a space to plot traces, and the axes, title and legend of the graph.
 * 
 *
 * XY-Graph Figure.
 * 
 * @author Xihui Chen
 * @author Kay Kasemir (performStagger)
 */
public class XYGraph extends Figure implements IXYGraph {

	private static final int GAP = 2;
	// public final static Color WHITE_COLOR = ColorConstants.white;
	// public final static Color BLACK_COLOR = ColorConstants.black;

	/**
	 * Default colors for newly added item, used over when reaching the end.
	 * <p>
	 * Very hard to find a long list of distinct colors. This list is definitely
	 * too short...
	 */
	final public static RGB[] DEFAULT_TRACES_COLOR = { new RGB(21, 21, 196), // blue
			new RGB(242, 26, 26), // red
			new RGB(33, 179, 33), // green
			new RGB(0, 0, 0), // black
			new RGB(128, 0, 255), // violet
			new RGB(255, 170, 0), // (darkish) yellow
			new RGB(255, 0, 240), // pink
			new RGB(243, 132, 132), // peachy
			new RGB(0, 255, 11), // neon green
			new RGB(0, 214, 255), // neon blue
			new RGB(114, 40, 3), // brown
			new RGB(219, 128, 4), // orange
	};

	protected boolean transparent = false;
	protected boolean showLegend = true;

	protected Map<Axis, Legend> legendMap;

	/**
	 * Graph title. Should never be <code>null</code> because otherwise the
	 * ToolbarArmedXYGraph's GraphConfigPage can crash.
	 */
	private String title = "";
	private Color titleColor;
	protected Label titleLabel;

	protected List<Axis> xAxisList;
	protected List<Axis> yAxisList;
	protected PlotArea plotArea;

	/**
	 * Use {@link #getPrimaryXAxis()} instead
	 */
	@Deprecated
	final public Axis primaryXAxis;
	/**
	 * Use {@link #getPrimaryYAxis()} instead
	 */
	@Deprecated
	final public Axis primaryYAxis;

	protected OperationsManager operationsManager;

	private ZoomType zoomType;

	/**
	 * Constructor.
	 */
	public XYGraph() {
		this(new DefaultAxesFactory());
	}

	/**
	 * Constructor.
	 * 
	 * @param axesFactory
	 *            The {@link IAxesFactory} to use to create the primary axes for
	 *            the graph. Should not be {@code null}
	 */
	public XYGraph(IAxesFactory axesFactory) {
		setOpaque(!transparent);
		legendMap = new LinkedHashMap<Axis, Legend>();
		titleLabel = new Label();
		String sysFontName = Display.getCurrent().getSystemFont().getFontData()[0].getName();
		setTitleFont(XYGraphMediaFactory.getInstance().getFont(new FontData(sysFontName, 12, SWT.BOLD)));
		// titleLabel.setVisible(false);
		xAxisList = new ArrayList<Axis>();
		yAxisList = new ArrayList<Axis>();
		plotArea = createPlotArea(this);
		getPlotArea().setOpaque(!transparent);

		add(titleLabel);
		add(plotArea);
		primaryYAxis = axesFactory.createYAxis();
		addAxis(primaryYAxis);

		primaryXAxis = axesFactory.createXAxis();
		addAxis(primaryXAxis);

		operationsManager = new OperationsManager();
	}

	protected PlotArea createPlotArea(IXYGraph xyGraph) {
		return new PlotArea(xyGraph);
	}

	@Override
	public boolean isOpaque() {
		return false;
	}

	@Override
	protected void layout() {
		Rectangle clientArea = getClientArea().getCopy();
		boolean hasRightYAxis = false;
		boolean hasTopXAxis = false;
		boolean hasLeftYAxis = false;
		boolean hasBottomXAxis = false;
		if (titleLabel != null && titleLabel.isVisible() && !(titleLabel.getText().length() <= 0)) {
			Dimension titleSize = titleLabel.getPreferredSize();
			titleLabel.setBounds(new Rectangle(clientArea.x + clientArea.width / 2 - titleSize.width / 2, clientArea.y,
					titleSize.width, titleSize.height));
			clientArea.y += titleSize.height + GAP;
			clientArea.height -= titleSize.height + GAP;
		}
		if (showLegend) {
			List<Integer> rowHPosList = new ArrayList<Integer>();
			List<Dimension> legendSizeList = new ArrayList<Dimension>();
			List<Integer> rowLegendNumList = new ArrayList<Integer>();
			List<Legend> legendList = new ArrayList<Legend>();
			Object[] yAxes = legendMap.keySet().toArray();
			int hPos = 0;
			int rowLegendNum = 0;
			for (int i = 0; i < yAxes.length; i++) {
				Legend legend = legendMap.get(yAxes[i]);
				if (legend != null && legend.isVisible()) {
					legendList.add(legend);
					Dimension legendSize = legend.getPreferredSize(clientArea.width, clientArea.height);
					legendSizeList.add(legendSize);
					if ((hPos + legendSize.width + GAP) > clientArea.width) {
						if (rowLegendNum == 0)
							break;
						rowHPosList.add(clientArea.x + (clientArea.width - hPos) / 2);
						rowLegendNumList.add(rowLegendNum);
						rowLegendNum = 1;
						hPos = legendSize.width + GAP;
						clientArea.height -= legendSize.height + GAP;
						if (i == yAxes.length - 1) {
							hPos = legendSize.width + GAP;
							rowLegendNum = 1;
							rowHPosList.add(clientArea.x + (clientArea.width - hPos) / 2);
							rowLegendNumList.add(rowLegendNum);
							clientArea.height -= legendSize.height + GAP;
						}
					} else {
						hPos += legendSize.width + GAP;
						rowLegendNum++;
						if (i == yAxes.length - 1) {
							rowHPosList.add(clientArea.x + (clientArea.width - hPos) / 2);
							rowLegendNumList.add(rowLegendNum);
							clientArea.height -= legendSize.height + GAP;
						}
					}
				}
			}
			int lm = 0;
			int vPos = clientArea.y + clientArea.height + GAP;
			for (int i = 0; i < rowLegendNumList.size(); i++) {
				hPos = rowHPosList.get(i);
				for (int j = 0; j < rowLegendNumList.get(i); j++) {
					legendList.get(lm).setBounds(
							new Rectangle(hPos, vPos, legendSizeList.get(lm).width, legendSizeList.get(lm).height));
					hPos += legendSizeList.get(lm).width + GAP;
					lm++;
				}
				vPos += legendSizeList.get(lm - 1).height + GAP;
			}
		}

		for (int i = xAxisList.size() - 1; i >= 0; i--) {
			Axis xAxis = xAxisList.get(i);
			Dimension xAxisSize = xAxis.getPreferredSize(clientArea.width, clientArea.height);
			if (xAxis.getTickLabelSide() == LabelSide.Primary) {
				if (xAxis.isVisible())
					hasBottomXAxis = true;
				xAxis.setBounds(new Rectangle(clientArea.x, clientArea.y + clientArea.height - xAxisSize.height,
						xAxisSize.width, xAxisSize.height));
				clientArea.height -= xAxisSize.height;
			} else {
				if (xAxis.isVisible())
					hasTopXAxis = true;
				xAxis.setBounds(new Rectangle(clientArea.x, clientArea.y + 1, xAxisSize.width, xAxisSize.height));
				clientArea.y += xAxisSize.height;
				clientArea.height -= xAxisSize.height;
			}
		}

		for (int i = yAxisList.size() - 1; i >= 0; i--) {
			Axis yAxis = yAxisList.get(i);
			int hintHeight = clientArea.height + (hasTopXAxis ? yAxis.getMargin() : 0)
					+ (hasBottomXAxis ? yAxis.getMargin() : 0);
			if (hintHeight > getClientArea().height)
				hintHeight = clientArea.height;
			Dimension yAxisSize = yAxis.getPreferredSize(clientArea.width, hintHeight);
			if (yAxis.getTickLabelSide() == LabelSide.Primary) { // on the left
				if (yAxis.isVisible())
					hasLeftYAxis = true;
				yAxis.setBounds(new Rectangle(clientArea.x, clientArea.y - (hasTopXAxis ? yAxis.getMargin() : 0),
						yAxisSize.width, yAxisSize.height));
				clientArea.x += yAxisSize.width;
				clientArea.width -= yAxisSize.width;
			} else { // on the right
				if (yAxis.isVisible())
					hasRightYAxis = true;
				yAxis.setBounds(new Rectangle(clientArea.x + clientArea.width - yAxisSize.width - 1,
						clientArea.y - (hasTopXAxis ? yAxis.getMargin() : 0), yAxisSize.width, yAxisSize.height));
				clientArea.width -= yAxisSize.width;
			}
		}

		// re-adjust xAxis bounds
		for (int i = xAxisList.size() - 1; i >= 0; i--) {
			Axis xAxis = xAxisList.get(i);
			Rectangle r = xAxis.getBounds().getCopy();
			if (hasLeftYAxis)
				r.x = clientArea.x - xAxis.getMargin() - 1;
			r.width = clientArea.width + (hasLeftYAxis ? xAxis.getMargin() : -1)
					+ (hasRightYAxis ? xAxis.getMargin() : 0);
			xAxis.setBounds(r);
		}

		if (plotArea != null && plotArea.isVisible()) {

			Rectangle plotAreaBound = new Rectangle(primaryXAxis.getBounds().x + primaryXAxis.getMargin(),
					primaryYAxis.getBounds().y + primaryYAxis.getMargin(), primaryXAxis.getTickLength(),
					primaryYAxis.getTickLength());
			plotArea.setBounds(plotAreaBound);

		}

		super.layout();
	}

	/**
	 * @param zoomType
	 *            the zoomType to set
	 */
	public void setZoomType(ZoomType zoomType) {
		this.zoomType = zoomType;
		plotArea.setZoomType(zoomType);
		for (Axis axis : xAxisList)
			axis.setZoomType(zoomType);
		for (Axis axis : yAxisList)
			axis.setZoomType(zoomType);
	}

	/**
	 * @return the zoomType
	 */
	public ZoomType getZoomType() {
		return zoomType;
	}

	/**
	 * @param title
	 *            the title to set
	 */
	public void setTitle(String title) {
		this.title = title.trim();
		titleLabel.setText(title);
	}

	/**
	 * @param showTitle
	 *            true if title should be shown; false otherwise.
	 */
	public void setShowTitle(boolean showTitle) {
		titleLabel.setVisible(showTitle);
		revalidate();
	}

	/**
	 * @return true if title should be shown; false otherwise.
	 */
	public boolean isShowTitle() {
		return titleLabel.isVisible();
	}

	/**
	 * @param showLegend
	 *            true if legend should be shown; false otherwise.
	 */
	public void setShowLegend(boolean showLegend) {
		this.showLegend = showLegend;
		for (Axis yAxis : legendMap.keySet()) {
			Legend legend = legendMap.get(yAxis);
			legend.setVisible(showLegend);
		}
		revalidate();
	}

	/**
	 * @return the showLegend
	 */
	public boolean isShowLegend() {
		return showLegend;
	}

	/**
	 * Add an axis to the graph
	 * 
	 * @param axis
	 */
	public void addAxis(Axis axis) {
		if (axis.isHorizontal())
			xAxisList.add(axis);
		else
			yAxisList.add(axis);
		plotArea.addGrid(new Grid(axis));
		add(axis);
		axis.setXyGraph((IXYGraph) this);
		revalidate();
	}

	/**
	 * Remove an axis from the graph
	 * 
	 * @param axis
	 * @return true if this axis exists.
	 */
	public boolean removeAxis(Axis axis) {
		remove(axis);
		plotArea.removeGrid(axis.getGrid());
		revalidate();
		if (axis.isHorizontal())
			return xAxisList.remove(axis);
		else
			return yAxisList.remove(axis);
	}

	/**
	 * Add a trace
	 * 
	 * @param trace
	 */
	public void addTrace(Trace trace) {
		addTrace(trace, null, null);
	}

	/**
	 * Add a trace
	 * 
	 * @param trace
	 */
	public void addTrace(Trace trace, Axis xAxis, Axis yAxis) {
		if (trace.getTraceColor() == null) { // Cycle through default colors
			trace.setTraceColor(XYGraphMediaFactory.getInstance()
					.getColor(DEFAULT_TRACES_COLOR[plotArea.getTraceList().size() % DEFAULT_TRACES_COLOR.length]));
		}
		if (legendMap.containsKey(trace.getYAxis()))
			legendMap.get(trace.getYAxis()).addTrace(trace);
		else {
			legendMap.put(trace.getYAxis(), new Legend((IXYGraph) this));
			legendMap.get(trace.getYAxis()).addTrace(trace);
			add(legendMap.get(trace.getYAxis()));
		}

		if (xAxis == null || yAxis == null) {
			try {
				for (Axis axis : getAxisList()) {
					axis.addTrace(trace);
				}
			} catch (Throwable ne) {
				// Ignored, this is a bug fix for Dawn 1.0
				// to make the plots rescale after a plot is deleted.
			}
		} else {
			xAxis.addTrace(trace);
			yAxis.addTrace(trace);
		}

		plotArea.addTrace(trace);
		trace.setXYGraph((IXYGraph) this);
		trace.dataChanged(null);
		revalidate();
		repaint();
	}

	/**
	 * Remove a trace.
	 * 
	 * @param trace
	 */
	public void removeTrace(Trace trace) {
		if (legendMap.containsKey(trace.getYAxis())) {
			legendMap.get(trace.getYAxis()).removeTrace(trace);
			if (legendMap.get(trace.getYAxis()).getTraceList().size() <= 0) {
				remove(legendMap.remove(trace.getYAxis()));
			}
		}
		try {
			for (Axis axis : getAxisList()) {
				axis.removeTrace(trace);
			}
		} catch (Throwable ne) {
			// Ignored, this is a bug fix for Dawn 1.0
			// to make the plots rescale after a plot is deleted.
		}
		plotArea.removeTrace(trace);
		revalidate();
		repaint();
	}

	/**
	 * Add an annotation
	 * 
	 * @param annotation
	 */
	public void addAnnotation(Annotation annotation) {
		plotArea.addAnnotation(annotation);
	}

	/**
	 * Remove an annotation
	 * 
	 * @param annotation
	 */
	public void removeAnnotation(Annotation annotation) {
		plotArea.removeAnnotation(annotation);
	}

	/**
	 * @param titleFont
	 *            the titleFont to set
	 */
	public void setTitleFont(Font titleFont) {
		titleLabel.setFont(titleFont);
	}

	/**
	 * @return the title font.
	 */
	public Font getTitleFont() {
		return titleLabel.getFont();
	}

	/**
	 * @param titleColor
	 *            the titleColor to set
	 */
	public void setTitleColor(Color titleColor) {
		this.titleColor = titleColor;
		titleLabel.setForegroundColor(titleColor);
	}

	/**
	 * {@inheritDoc}
	 */
	public void paintFigure(final Graphics graphics) {
		if (!transparent) {
			graphics.fillRectangle(getClientArea());
		}
		super.paintFigure(graphics);
	}

	/**
	 * @param transparent
	 *            the transparent to set
	 */
	public void setTransparent(boolean transparent) {
		this.transparent = transparent;
		getPlotArea().setOpaque(!transparent);
		repaint();
	}

	/**
	 * @return the transparent
	 */
	public boolean isTransparent() {
		return transparent;
	}

	/**
	 * @return the plotArea, which contains all the elements drawn inside it.
	 */
	public PlotArea getPlotArea() {
		return plotArea;
	}

	/** @return Image of the XYFigure. Receiver must dispose. */
	public Image getImage() {
		return SingleSourceHelper2.getXYGraphSnapShot(this);
	}

	/**
	 * @return the titleColor
	 */
	public Color getTitleColor() {
		if (titleColor == null)
			return getForegroundColor();
		return titleColor;
	}

	/**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * @return the operationsManager
	 */
	public OperationsManager getOperationsManager() {
		return operationsManager;
	}

	/**
	 * @return the xAxisList
	 */
	public List<Axis> getXAxisList() {
		return xAxisList;
	}

	/**
	 * @return the yAxisList
	 */
	public List<Axis> getYAxisList() {
		return yAxisList;
	}

	/**
	 * @return the all the axis include xAxes and yAxes. yAxisList is appended
	 *         to xAxisList in the returned list.
	 */
	public List<Axis> getAxisList() {
		List<Axis> list = new ArrayList<Axis>();
		list.addAll(xAxisList);
		list.addAll(yAxisList);
		return list;
	}

	/**
	 * @return the legendMap
	 */
	public Map<Axis, Legend> getLegendMap() {
		return legendMap;
	}

	/**
	 * Perform forced autoscale to all axes.
	 */
	public void performAutoScale() {
		final ZoomCommand command = new ZoomCommand("Auto Scale", xAxisList, yAxisList);
		for (Axis axis : xAxisList) {
			if (!axis.isVisible())
				continue;
			axis.performAutoScale(true);
		}
		for (Axis axis : yAxisList) {
			if (!axis.isVisible())
				continue;
			axis.performAutoScale(true);
		}
		command.saveState();
		operationsManager.addCommand(command);
	}

	/**
	 * Stagger all axes: Autoscale each axis so that traces on various axes
	 * don't overlap
	 */
	public void performStagger() {
		final double GAP = 0.1;

		final ZoomCommand command = new ZoomCommand("Stagger Axes", null, yAxisList);

		// Arrange all axes so they don't overlap by assigning 1/Nth of
		// the vertical range to each one
		final int N = yAxisList.size();
		for (int i = 0; i < N; ++i) {
			final Axis yaxis = yAxisList.get(i);
			// Does axis handle itself in another way?
			if (yaxis.isAutoScale())
				continue;

			// Determine range of values on this axis
			final Range axis_range = yaxis.getTraceDataRange();
			// Skip axis which for some reason cannot determine its range
			if (axis_range == null)
				continue;

			double low = axis_range.getLower();
			double high = axis_range.getUpper();
			if (low == high) { // Center trace with constant value (empty range)
				final double half = Math.abs(low / 2);
				low -= half;
				high += half;
			}

			if (yaxis.isLogScaleEnabled()) { // Transition into log space
				low = Log10.log10(low);
				high = Log10.log10(high);
			}

			double span = high - low;
			// Make some extra space
			low -= GAP * span;
			high += GAP * span;
			span = high - low;

			// With N axes, assign 1/Nth of the vertical plot space to this axis
			// by shifting the span down according to the axis index,
			// using a total of N*range.
			low -= (N - i - 1) * span;
			high += i * span;

			if (yaxis.isLogScaleEnabled()) { // Revert from log space
				low = Log10.pow10(low);
				high = Log10.pow10(high);
			}

			// Sanity check for empty traces
			if (low < high && !Double.isInfinite(low) && !Double.isInfinite(high))
				yaxis.setRange(low, high);
		}

		command.saveState();
		operationsManager.addCommand(command);
	}

	/**
	 * @param trim
	 * @return Image of the XYFigure. Receiver must dispose.
	 */
	public Image getImage(org.eclipse.swt.graphics.Rectangle size) {

		Rectangle orig = new Rectangle(bounds);

		try {
			setBounds(new Rectangle(0, 0, size.width, size.height));
			layout();
			plotArea.layout();
			plotArea.layout();
			primaryYAxis.layout();
			primaryXAxis.layout();

			Image image = new Image(null, bounds.width + 6, bounds.height + 6);
			GC gc = GraphicsUtil.createGC(image);

			SWTGraphics graphics = new SWTGraphics(gc);
			// Needed because the clipping is not set with GTK2
			graphics.setClip(new Rectangle(0, 0, image.getBounds().width, image.getBounds().height));
			graphics.translate(-bounds.x + 3, -bounds.y + 3);
			graphics.setForegroundColor(getForegroundColor());
			graphics.setBackgroundColor(getBackgroundColor());
			paint(graphics);
			gc.dispose();
			return image;

		} finally {
			setBounds(orig);
			layout();
			plotArea.layout();
			plotArea.layout();
			primaryYAxis.layout();
			primaryXAxis.layout();
		}
	}

	@Override
	public Axis getPrimaryXAxis() {
		if (xAxisList.size() > 0) {
			return xAxisList.get(0);
		}
		return null;
	}

	@Override
	public Axis getPrimaryYAxis() {
		if (yAxisList.size() > 0) {
			return yAxisList.get(0);
		}
		return null;
	}
}
