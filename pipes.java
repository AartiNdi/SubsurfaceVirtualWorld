/* Copyright (C) 2001, 2011 United States Government as represented by 
the Administrator of the National Aeronautics and Space Administration. 
All Rights Reserved. 
 * 
 * @author Aarti K. Dwivedi
*/


package gov.nasa.worldwindx.examples.analytics2;

import gov.nasa.worldwindx.examples.ApplicationTemplate;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.data.BufferWrapperRaster;
import gov.nasa.worldwind.util.WWIO;
import gov.nasa.worldwind.util.WWBufferUtil;
import gov.nasa.worldwind.util.WWMath;
import gov.nasa.worldwind.util.BufferWrapper;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.geom.Extent;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.avlist.*;
import gov.nasa.worldwind.formats.worldfile.WorldFile;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.Format;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.net.URI;
import java.net.URLClassLoader;

public class Rocks extends ApplicationTemplate
{
	public static class AppFrame extends ApplicationTemplate.AppFrame
	{
		public AppFrame()
		{
			// Add detail hint slider panel
            this.getLayerPanel().add(this.makeDetailHintControlPanel(), BorderLayout.SOUTH);

            RenderableLayer layer = new RenderableLayer();

            // Create and set an attribute bundle.
            ShapeAttributes attrs = new BasicShapeAttributes();
            attrs.setInteriorMaterial(Material.YELLOW);
            attrs.setInteriorOpacity(0.3);
            attrs.setEnableLighting(true);
            attrs.setOutlineMaterial(Material.RED);
            attrs.setOutlineWidth(2d);
            attrs.setDrawInterior(true);
            attrs.setDrawOutline(false);

            //GDAL configuration
			gdal.SetConfigOption("GDAL_DATA", "data");
	        ogr.RegisterAll();
	        Feature feat;

	        //assuming I extract data to rockData from source stringWithData
	        String stringWithData = "/home/vahni/projects/gsoc/pipe.gml";
	        DataSource rockData;
	        rockData  = ogr.Open(stringWIthData);
	        Feature pipe;

	        while((pipe = poDS.GetLayerByIndex(0).GetNextFeature()) != null){
                //System.out.println("Name: "+feat.GetFieldAsString("gml_id"));
                //System.out.println("Lon: "+feat.GetGeometryRef().GetX());
                //System.out.println("Lat: "+feat.GetGeometryRef().GetY());
                //System.out.println("Elev: "+feat.GetFieldAsString("elevation"));
                double elevation = Double.parseDouble(pipe.GetFieldAsString("elevation"))*10;
                Cylinder2 mycylinder = new Cylinder2(Position.fromDegrees(feat.GetGeometryRef().GetY(), feat.GetGeometryRef().GetX(), (elevation/2)-elevation), elevation , 20);
                mycylinder.setAltitudeMode(WorldWind.RELATIVE_TO_GROUND);
                mycylinder.setAttributes(attrs);
                mycylinder.setVisible(true);
                mycylinder.setValue(AVKey.DISPLAY_NAME, "Cylinder with equal axes, Relative altitude mode");
                layer.addRenderable(mycylinder);
                
            }

            //Make transparent the layers
            for (Layer layer : this.getWwd().getModel().getLayers()){
                layer.setOpacity(0.8);
            }

            // Add the layer to the model.
            insertBeforeCompass(getWwd(), layer);
            // Update layer panel
            this.getLayerPanel().update(this.getWwd());
        }

        protected void initAnalyticSurfaceLayer()
        {
            this.analyticSurfaceLayer = new RenderableLayer();
            this.analyticSurfaceLayer.setPickEnabled(false);
            this.analyticSurfaceLayer.setName("Analytic Surfaces");
            insertBeforePlacenames(this.getWwd(), this.analyticSurfaceLayer);
            this.getLayerPanel().update(this.getWwd());

            //createRandomAltitudeSurface(HUE_BLUE, HUE_RED, 40, 40, this.analyticSurfaceLayer);
            //createRandomColorSurface(HUE_BLUE, HUE_RED, 40, 40, this.analyticSurfaceLayer);

            // Load the static precipitation data. Since it comes over the network, load it in a separate thread to
            // avoid blocking the example if the load is slow or fails.
            Thread t = new Thread(new Runnable()
            {
                public void run()
                {
                    createPrecipitationSurface(HUE_BLUE, HUE_RED, analyticSurfaceLayer);
                }
            });
            t.start();
        }
	}

		protected JPanel makeDetailHintControlPanel()
        {
            JPanel controlPanel = new JPanel(new BorderLayout(0, 10));
            controlPanel.setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9),
                new TitledBorder("Detail Hint")));

            JPanel detailHintSliderPanel = new JPanel(new BorderLayout(0, 5));
            {
                int MIN = -10;
                int MAX = 10;
                int cur = 0;
                JSlider slider = new JSlider(MIN, MAX, cur);
                slider.setMajorTickSpacing(10);
                slider.setMinorTickSpacing(1);
                slider.setPaintTicks(true);
                Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
                labelTable.put(-10, new JLabel("-1.0"));
                labelTable.put(0, new JLabel("0.0"));
                labelTable.put(10, new JLabel("1.0"));
                slider.setLabelTable(labelTable);
                slider.setPaintLabels(true);
                slider.addChangeListener(new ChangeListener()
                {
                    public void stateChanged(ChangeEvent e)
                    {
                        double hint = ((JSlider) e.getSource()).getValue() / 10d;
                        setCylinderDetailHint(hint);
                        getWwd().redraw();
                    }
                });
                detailHintSliderPanel.add(slider, BorderLayout.SOUTH);
            }

            JPanel sliderPanel = new JPanel(new GridLayout(2, 0));
            sliderPanel.add(detailHintSliderPanel);

            controlPanel.add(sliderPanel, BorderLayout.SOUTH);
            return controlPanel;
        }

        protected void setCylinderDetailHint(double hint)
        {
            for (Renderable renderable : getLayer().getRenderables())
            {
                Cylinder2 current = (Cylinder2) renderable;
                current.setDetailHint(hint);
            }
            System.out.println("cylinder detail hint set to " + hint);
        }

		protected RenderableLayer getLayer()
        {
            for (Layer layer : getWwd().getModel().getLayers())
            {
                if (layer.getName().contains("Renderable"))
                {
                    return (RenderableLayer) layer;
                }
            }

            return null;
        }

	    protected static ByteBuffer unzipEntryToBuffer(ZipFile zipFile, String entryName) throws IOException
    	{
        	ZipEntry entry = zipFile.getEntry(entryName);
	        InputStream is = zipFile.getInputStream(entry);
    	    return WWIO.readStreamToBuffer(is);
    	}

    	protected static BufferWrapperRaster loadZippedBILData(String uriString)
    {
        try
        {
            File zipFile = File.createTempFile("data", ".zip");
            File hdrFile = File.createTempFile("data", ".hdr");
            File blwFile = File.createTempFile("data", ".blw");
            zipFile.deleteOnExit();
            hdrFile.deleteOnExit();
            blwFile.deleteOnExit();

            ByteBuffer byteBuffer = WWIO.readURLContentToBuffer(new URI(uriString).toURL());
            WWIO.saveBuffer(byteBuffer, zipFile);

            ZipFile zip = new ZipFile(zipFile);
            ByteBuffer dataBuffer = unzipEntryToBuffer(zip, "data.bil");
            WWIO.saveBuffer(unzipEntryToBuffer(zip, "data.hdr"), hdrFile);
            WWIO.saveBuffer(unzipEntryToBuffer(zip, "data.blw"), blwFile);
            zip.close();

            AVList params = new AVListImpl();
            WorldFile.decodeWorldFiles(new File[] {hdrFile, blwFile}, params);
            params.setValue(AVKey.DATA_TYPE, params.getValue(AVKey.PIXEL_TYPE));

            Double missingDataSignal = (Double) params.getValue(AVKey.MISSING_DATA_REPLACEMENT);
            if (missingDataSignal == null)
                missingDataSignal = Double.NaN;

            Sector sector = (Sector) params.getValue(AVKey.SECTOR);
            int[] dimensions = (int[]) params.getValue(WorldFile.WORLD_FILE_IMAGE_SIZE);
            BufferWrapper buffer = BufferWrapper.wrap(dataBuffer, params);

            BufferWrapperRaster raster = new BufferWrapperRaster(dimensions[0], dimensions[1], sector, buffer);
            raster.setTransparentValue(missingDataSignal);
            return raster;
        }
        catch (Exception e)
        {
            String message = Logging.getMessage("generic.ExceptionAttemptingToReadFrom", uriString);
            Logging.logger().severe(message);
            return null;
        }
    }
	}

	public static void main(String[] args)
    {
        ApplicationTemplate.start("World Wind Cylinders", AppFrame.class);
    }
}