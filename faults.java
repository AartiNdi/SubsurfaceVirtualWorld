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

public class faults extends ApplicationTemplate {
	public static class AppFrame extends ApplicationTemplate.AppFrame {
		public AppFrame()
		{
			//GDAL configuration
			gdal.SetConfigOption("GDAL_DATA", "data");
	        ogr.RegisterAll();
	        Feature feat;

	        //assuming I extract data to rockData from source stringWithData
	        String stringWithData = "/home/vahni/projects/gsoc/pipe.gml";
	        DataSource rockData;
	        rockData  = ogr.Open(stringWIthData);
	        Feature pipe;
		}

		protected static Renderable createLegendRenderable(final AnalyticSurface surface, final double surfaceMinScreenSize,
        	final AnalyticSurfaceLegend legend)
    	{
        	return new Renderable()
        	{
            	public void render(DrawContext dc)
            	{
	                Extent extent = surface.getExtent(dc);
    	            if (!extent.intersects(dc.getView().getFrustumInModelCoordinates()))
        	            return;

	                if (WWMath.computeSizeInWindowCoordinates(dc, extent) < surfaceMinScreenSize)
    	                return;

        	        legend.render(dc);
            	}
        	};
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

    	protected static ByteBuffer unzipEntryToBuffer(ZipFile zipFile, String entryName) throws IOException
    	{
        	ZipEntry entry = zipFile.getEntry(entryName);
	        InputStream is = zipFile.getInputStream(entry);
    	    return WWIO.readStreamToBuffer(is);
    	}

    	protected static void createFault(double minHue, double maxHue, final RenderableLayer outLayer)
    	{
            BufferWrapperRaster raster = loadZippedBILData(
            //"http://worldwind.arc.nasa.gov/java/demos/data/wa-precip-24hmam.zip");
                "http://forecast.chapman.edu/nickhatz/data.zip");
            if (raster == null)
                return;

            double[] extremes = WWBufferUtil.computeExtremeValues(raster.getBuffer(), raster.getTransparentValue());
            if (extremes == null)
                return;        

            final AnalyticSurface surface = new AnalyticSurface();
            surface.setSector(raster.getSector());
            surface.setDimensions(raster.getWidth(), raster.getHeight());
        
            surface.setValues(AnalyticSurface.createColorGradientValues(
                raster.getBuffer(), raster.getTransparentValue(), extremes[0], extremes[1], minHue, maxHue));
            surface.setVerticalScale(-100);

            AnalyticSurfaceAttributes attr = new AnalyticSurfaceAttributes();
            attr.setDrawOutline(true);
            attr.setDrawShadow(false);
            attr.setInteriorOpacity(0.3);
            surface.setSurfaceAttributes(attr);

            Format legendLabelFormat = new DecimalFormat("# ft")
            {
                public StringBuffer format(double number, StringBuffer result, FieldPosition fieldPosition)
                {
                    double valueInFeet = number * WWMath.METERS_TO_FEET;
                    return super.format(valueInFeet, result, fieldPosition);
                }
            };
        
            final AnalyticSurfaceLegend legend = AnalyticSurfaceLegend.fromColorGradient(extremes[0], extremes[1],
                minHue, maxHue,
                AnalyticSurfaceLegend.createDefaultTitle("Fault"));
            legend.setScreenLocation(new Point(100, 300));
         
        
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    surface.setClientLayer(outLayer);
                    outLayer.addRenderable(surface);
                    outLayer.addRenderable(createLegendRenderable(surface, 300, legend));
                }
            });
    	}
	}

	public static void main(String[] args)
    {
        ApplicationTemplate.start("World Wind Cylinders", AppFrame.class);
    }
}