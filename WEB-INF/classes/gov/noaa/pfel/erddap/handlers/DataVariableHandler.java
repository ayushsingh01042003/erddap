package gov.noaa.pfel.erddap.handlers;

import com.cohort.util.String2;
import gov.noaa.pfel.erddap.dataset.metadata.LocalizedAttributes;
import gov.noaa.pfel.erddap.variable.DataVariableInfo;
import java.util.List;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class DataVariableHandler extends StateWithParent {
  final StringBuilder content = new StringBuilder();
  String tSourceName = null, tDestinationName = null, tDataType = null;
  final LocalizedAttributes tAttributes = new LocalizedAttributes();
  final List<DataVariableInfo> tDataVariables;

  public DataVariableHandler(
      SaxHandler saxHandler, List<DataVariableInfo> tDataVariables, State completeState) {
    super(saxHandler, completeState);
    this.tDataVariables = tDataVariables;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes)
      throws SAXException {
    if (localName.equals("addAttributes")) {
      State state = new AddAttributesHandler(saxHandler, tAttributes, this);
      saxHandler.setState(state);
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    content.append(ch, start, length);
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws Throwable {
    String contentStr = content.toString().trim();

    switch (localName) {
      case "sourceName" -> tSourceName = contentStr;
      case "destinationName" -> tDestinationName = contentStr;
      case "dataType" -> tDataType = contentStr;
      case "dataVariable" -> {
        tDataVariables.add(
            new DataVariableInfo(tSourceName, tDestinationName, tAttributes, tDataType));
        saxHandler.setState(this.completeState);
      }
      default -> String2.log("Unexpected end tag: " + localName);
    }
    content.setLength(0);
  }
}
