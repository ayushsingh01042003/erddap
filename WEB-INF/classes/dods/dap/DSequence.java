/////////////////////////////////////////////////////////////////////////////
// Copyright (c) 1998, California Institute of Technology.
// ALL RIGHTS RESERVED.   U.S. Government Sponsorship acknowledged.
//
// Please read the full copyright notice in the file COPYRIGHT
// in this directory.
//
// Author: Jake Hamby, NASA/Jet Propulsion Laboratory
//         Jake.Hamby@jpl.nasa.gov
/////////////////////////////////////////////////////////////////////////////

package dods.dap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A <code>DSequence</code> in DODS can hold <em>N</em> sequentially accessed instances of a set of
 * variables. In relation to the <code>DStructure</code> datatype, a <code>DSequence</code> is a
 * table of N instances of a <code>DStructure</code>. Data in a <code>DSequence</code> is accessed
 * row by row.
 *
 * <p>Unlike its C++ counterpart, this class reads all of its rows on a <code>deserialize</code>,
 * which gives <code>DSequence</code> the same semantics as the other <code>BaseType</code> classes,
 * eliminating the need to worry about <code>DSequence</code> as a special case.
 *
 * @version $Revision: 1.11 $
 * @author jehamby
 * @see BaseType
 * @see DConstructor
 */
public class DSequence extends DConstructor implements ClientIO {
  /** The start of instance byte marker */
  protected static final byte START_OF_INSTANCE = 0x5A;

  /** The end of sequence byte marker */
  protected static final byte END_OF_SEQUENCE = (byte) 0xA5;

  /**
   * The variables in this <code>DSequence</code>, stored in a <code>Vector</code> of <code>BaseType
   * </code> objects and used as a template for <code>deserialize</code>.
   */
  protected List<BaseType> varTemplate;

  /**
   * The values in this <code>DSequence</code>, stored as a <code>Vector</code> of <code>Vector
   * </code> of <code>BaseType</code> objects.
   */
  protected List<List<BaseType>> allValues;

  /** Level number in a multilevel sequence. */
  private int level;

  /** Constructs a new <code>DSequence</code>. */
  public DSequence() {
    this(null);
  }

  /**
   * Constructs a new <code>DSequence</code> with name <code>n</code>.
   *
   * @param n the name of the variable.
   */
  public DSequence(String n) {
    super(n);
    varTemplate = new ArrayList<>();
    allValues = new ArrayList<>();
    level = 0;
  }

  /**
   * Returns a clone of this <code>DSequence</code>. A deep copy is performed on all data inside the
   * variable.
   *
   * @return a clone of this <code>DSequence</code>.
   */
  @Override
  public DSequence clone() {

    DSequence s = (DSequence) super.clone();

    s.varTemplate = new ArrayList<>();

    for (int i = 0; i < varTemplate.size(); i++) {
      BaseType bt = varTemplate.get(i);
      s.varTemplate.add(bt.clone());
    }

    s.allValues = new ArrayList<>();

    for (int i = 0; i < allValues.size(); i++) {
      List<BaseType> rowVec = allValues.get(i);
      List<BaseType> newVec = new ArrayList<>();
      for (BaseType bt : rowVec) {
        newVec.add(bt.clone());
      }
      s.allValues.add(newVec);
    }
    return s;
  }

  /**
   * Returns the DODS type name of the class instance as a <code>String</code>.
   *
   * @return the DODS type name of the class instance as a <code>String</code>.
   */
  @Override
  public String getTypeName() {
    return "Sequence";
  }

  /**
   * Sets the level of this sequence.
   *
   * @param level the new level.
   */
  protected final void setLevel(int level) {
    this.level = level;
  }

  /**
   * Returns the level of this sequence.
   *
   * @return the level of this sequence.
   */
  protected final int getLevel() {
    return level;
  }

  /**
   * Returns the number of variables contained in this object. For simple and vector type variables,
   * it always returns 1. To count the number of simple-type variable in the variable tree rooted at
   * this variable, set <code>leaves</code> to <code>true</code>.
   *
   * @param leaves If true, count all the simple types in the `tree' of variables rooted at this
   *     variable.
   * @return the number of contained variables.
   */
  @Override
  public int elementCount(boolean leaves) {
    if (!leaves) return varTemplate.size();
    else {
      int count = 0;
      for (BaseType bt : varTemplate) {
        count += bt.elementCount(leaves);
      }
      return count;
    }
  }

  /**
   * Adds a variable to the container.
   *
   * @param v the variable to add.
   * @param part ignored for <code>DSequence</code>.
   */
  @Override
  public void addVariable(BaseType v, int part) {
    v.setParent(this);
    varTemplate.add(v);
    if (v instanceof DSequence) ((DSequence) v).setLevel(getLevel() + 1);
  }

  /**
   * Adds a row to the container. This is assumed to contain a <code>Vector</code> of variables of
   * the same type and in the same order as the variable template added with the <code>addVariable
   * </code> method.
   *
   * @param row the <code>Vector</code> to add.
   */
  public final void addRow(List<BaseType> row) {
    allValues.add(row);
  }

  /**
   * Gets a row from the container. This returns a <code>Vector</code> of variables of the same type
   * and in the same order as the variable template added with the <code>addVariable</code> method.
   *
   * @param row the row number to retrieve.
   * @return the <code>Vector</code> of <code>BaseType</code> variables.
   */
  public final List<BaseType> getRow(int row) {
    return allValues.get(row);
  }

  /**
   * Deletes a row from the container.
   *
   * @param row the row number to delete.
   * @exception ArrayIndexOutOfBoundsException if the index was invalid.
   */
  public final void delRow(int row) {
    allValues.remove(row);
  }

  /**
   * Returns the number of rows in this <code>Sequence</code>.
   *
   * @return the number of rows currently in this <code>Sequence</code>.
   */
  public int getRowCount() {
    return allValues.size();
  }

  /**
   * Returns the named variable. <strong>Note:</strong> In <code>DSequence</code>, this method
   * returns the template variable, which holds no data. If you need to get a variable containing
   * data, use <code>getRow</code> or the <code>getVariable</code> method which takes a row number
   * parameter.
   *
   * @param name the name of the variable.
   * @return the named variable.
   * @exception NoSuchVariableException if the named variable does not exist in this container.
   * @see DSequence#getVariable(int, String)
   */
  @Override
  public BaseType getVariable(String name) throws NoSuchVariableException {

    int dotIndex = name.indexOf('.');

    if (dotIndex != -1) { // name contains "."
      String aggregate = name.substring(0, dotIndex);
      String field = name.substring(dotIndex + 1);

      BaseType aggRef = getVariable(aggregate);
      if (aggRef instanceof DConstructor)
        return ((DConstructor) aggRef).getVariable(field); // recurse
      else
        ; // fall through to throw statement
    } else {
      for (BaseType v : varTemplate) {
        if (v.getName().equals(name)) return v;
      }
    }
    throw new NoSuchVariableException("DSequence: getVariable()");
  }

  /**
   * Gets the indexed variable. For a DSrquence this returns the <code>BaseType</code> from the
   * <code>index</code>th column from the internal map <code>Vector</code>.
   *
   * @param index the index of the variable in the <code>Vector</code> Vars.
   * @return the indexed variable.
   * @exception NoSuchVariableException if the named variable does not exist in this container.
   */
  @Override
  public BaseType getVar(int index) throws NoSuchVariableException {

    if (index < varTemplate.size()) return varTemplate.get(index);
    else throw new NoSuchVariableException("DSequence.getVariable(" + index + " - 1)");
  }

  /**
   * Returns the named variable in the given row of the sequence.
   *
   * @param row the row number to retrieve.
   * @param name the name of the variable.
   * @return the named variable.
   * @exception NoSuchVariableException if the named variable does not exist in this container.
   */
  public BaseType getVariable(int row, String name) throws NoSuchVariableException {

    int dotIndex = name.indexOf('.');

    if (dotIndex != -1) { // name contains "."
      String aggregate = name.substring(0, dotIndex);
      String field = name.substring(dotIndex + 1);

      BaseType aggRef = getVariable(aggregate);
      if (aggRef instanceof DConstructor)
        return ((DConstructor) aggRef).getVariable(field); // recurse
      else
        ; // fall through to throw statement
    } else {
      List<BaseType> selectedRow = allValues.get(row);
      for (BaseType v : selectedRow) {
        if (v.getName().equals(name)) return v;
      }
    }
    throw new NoSuchVariableException("DSequence: getVariable()");
  }

  /**
   * Return an Enumeration that can be used to iterate over the members of a Sequence. This
   * implementation provides access to the template elements of the Sequence, not the entire
   * sequence. Each Object returned by the Enumeration can be cast to a BaseType.
   *
   * @return An Enumeration
   */
  @Override
  public Iterator<BaseType> getVariables() {
    return varTemplate.iterator();
  }

  /**
   * Checks for internal consistency. For <code>DSequence</code>, verify that the variables have
   * unique names.
   *
   * @param all for complex constructor types, this flag indicates whether to check the semantics of
   *     the member variables, too.
   * @exception BadSemanticsException if semantics are bad, explains why.
   * @see BaseType#checkSemantics(boolean)
   */
  @Override
  public void checkSemantics(boolean all) throws BadSemanticsException {
    super.checkSemantics(all);

    Util.uniqueNames(varTemplate, getName(), getTypeName());

    if (all) {
      for (BaseType bt : varTemplate) {
        bt.checkSemantics(true);
      }
    }
  }

  /**
   * Write the variable's declaration in a C-style syntax. This function is used to create textual
   * representation of the Data Descriptor Structure (DDS). See <em>The DODS User Manual</em> for
   * information about this structure.
   *
   * @param os The <code>PrintWriter</code> on which to print the declaration.
   * @param space Each line of the declaration will begin with the characters in this string.
   *     Usually used for leading spaces.
   * @param print_semi a boolean value indicating whether to print a semicolon at the end of the
   *     declaration.
   * @see BaseType#printDecl(PrintWriter, String, boolean)
   */
  @Override
  public void printDecl(PrintWriter os, String space, boolean print_semi, boolean constrained) {

    // BEWARE! Since printDecl()is (multiple) overloaded in BaseType
    // and all of the different signatures of printDecl() in BaseType
    // lead to one signature, we must be careful to override that
    // SAME signature here. That way all calls to printDecl() for
    // this object lead to this implementation.

    os.println(space + getTypeName() + " {");
    for (BaseType bt : varTemplate) {
      // os.println("Printing declaration for \""+bt.getName()+"\"   constrained: "+constrained);
      bt.printDecl(os, space + "    ", true, constrained);
    }
    os.print(space + "} " + getName());
    if (print_semi) os.println(";");
  }

  /**
   * Prints the value of the variable, with its declaration. This function is primarily intended for
   * debugging DODS applications and text-based clients such as geturl.
   *
   * @param os the <code>PrintWriter</code> on which to print the value.
   * @param space this value is passed to the <code>printDecl</code> method, and controls the
   *     leading spaces of the output.
   * @param print_decl_p a boolean value controlling whether the variable declaration is printed as
   *     well as the value.
   * @see BaseType#printVal(PrintWriter, String, boolean)
   */
  @Override
  public void printVal(PrintWriter os, String space, boolean print_decl_p) {
    if (print_decl_p) {
      printDecl(os, space, false);
      os.print(" = ");
    }

    os.print("{ ");
    for (Iterator<List<BaseType>> e1 = allValues.iterator(); e1.hasNext(); ) {
      // get next instance vector
      os.print("{ ");
      List<BaseType> v = e1.next();
      for (Iterator<BaseType> e2 = v.iterator(); e2.hasNext(); ) {
        // get next instance variable
        BaseType bt = e2.next();
        bt.printVal(os, "", false);
        if (e2.hasNext()) os.print(", ");
      }
      os.print(" }");
      if (e1.hasNext()) os.print(", ");
    }
    os.print(" }");

    if (print_decl_p) os.println(";");
  }

  /**
   * Reads data from a <code>DataInputStream</code>. This method is only used on the client side of
   * the DODS client/server connection.
   *
   * @param source a <code>DataInputStream</code> to read from.
   * @param sv the <code>ServerVersion</code> returned by the server.
   * @param statusUI the <code>StatusUI</code> object to use for GUI updates and user cancellation
   *     notification (may be null).
   * @exception EOFException if EOF is found before the variable is completely deserialized.
   * @exception IOException thrown on any other InputStream exception.
   * @exception DataReadException if an unexpected value was read.
   * @see ClientIO#deserialize(DataInputStream, ServerVersion, StatusUI)
   */
  @Override
  public synchronized void deserialize(DataInputStream source, ServerVersion sv, StatusUI statusUI)
      throws IOException, EOFException, DataReadException {

    // check for old servers
    if (sv.getMajor() < 2 || (sv.getMajor() == 2 && sv.getMinor() < 15)) {
      oldDeserialize(source, sv, statusUI);
    } else {
      // ************* Pulled out the getLevel() check in order to support the "new"
      // and "improved" serialization of dods sequences. 8/31/01 ndp
      //            // top level of sequence handles start and end markers
      //            if (getLevel() == 0) {
      // loop until end of sequence
      for (; ; ) {

        byte marker = readMarker(source);

        if (statusUI != null) statusUI.incrementByteCount(4);

        if (marker == START_OF_INSTANCE) deserializeSingle(source, sv, statusUI);
        else if (marker == END_OF_SEQUENCE) break;
        else throw new DataReadException("Sequence start marker not found (marker=" + marker + ")");
      }
      // ************* Pulled out the getLevel() check in order to support the "new"
      // and "improved" serialization of dods sequences. 8/31/01 ndp
      //            }
      //	    else {
      //                // lower levels only deserialize a single instance at a time
      //                deserializeSingle(source, sv, statusUI);
      //            }
    }
  }

  /**
   * The old deserialize protocol has a number of limitations stemming from its inability to tell
   * when the sequence is finished. It's really only good for a Dataset containing a single
   * sequence, or where the sequence is the last thing in the dataset. To handle this, we just read
   * single instances until we get an IOException, then stop.
   *
   * @param source a <code>DataInputStream</code> to read from.
   * @param sv the <code>ServerVersion</code> returned by the server.
   * @param statusUI the <code>StatusUI</code> object to use for GUI updates and user cancellation
   *     notification (may be null).
   * @exception IOException thrown on any InputStream exception other than EOF (which is trapped
   *     here).
   * @exception DataReadException if an unexpected value was read.
   */
  private void oldDeserialize(DataInputStream source, ServerVersion sv, StatusUI statusUI)
      throws IOException, DataReadException {
    try {
      for (; ; ) {
        deserializeSingle(source, sv, statusUI);
      }
    } catch (EOFException e) {
    }
  }

  /**
   * Deserialize a single row of the <code>DSequence</code>.
   *
   * @param source a <code>DataInputStream</code> to read from.
   * @param sv the <code>ServerVersion</code> returned by the server.
   * @param statusUI the <code>StatusUI</code> object to use for GUI updates and user cancellation
   *     notification (may be null).
   * @exception EOFException if EOF is found before the variable is completely deserialized.
   * @exception IOException thrown on any other InputStream exception.
   * @exception DataReadException if an unexpected value was read.
   */
  private void deserializeSingle(DataInputStream source, ServerVersion sv, StatusUI statusUI)
      throws IOException, EOFException, DataReadException {
    // create a new instance from the variable template Vector
    List<BaseType> newInstance = new ArrayList<>();
    for (BaseType bt : varTemplate) {
      newInstance.add(bt.clone());
    }
    // deserialize the new instance
    for (BaseType base : newInstance) {
      if (statusUI != null && statusUI.userCancelled())
        throw new DataReadException("User cancelled");
      ClientIO bt = (ClientIO) base;
      bt.deserialize(source, sv, statusUI);
    }
    // add the new instance to the allValues vector
    allValues.add(newInstance);
  }

  /** Reads a marker byte from the input stream. */
  private byte readMarker(DataInputStream source) throws IOException {
    byte marker = source.readByte();
    // pad out to a multiple of four bytes
    @SuppressWarnings("unused")
    byte unused;
    for (int i = 0; i < 3; i++) unused = source.readByte();

    return marker;
  }

  /** Writes a marker byte to the output stream. */
  protected void writeMarker(DataOutputStream sink, byte marker) throws IOException {
    // for(int i=0; i<4; i++)
    sink.writeByte(marker);
    sink.writeByte((byte) 0);
    sink.writeByte((byte) 0);
    sink.writeByte((byte) 0);
  }

  /**
   * Writes data to a <code>DataOutputStream</code>. This method is used primarily by GUI clients
   * which need to download DODS data, manipulate it, and then re-save it as a binary file.
   *
   * @param sink a <code>DataOutputStream</code> to write to.
   * @exception IOException thrown on any <code>OutputStream</code> exception.
   */
  @Override
  public void externalize(DataOutputStream sink) throws IOException {

    // loop until end of sequence
    for (List<BaseType> allValue : allValues) {

      // ************* Pulled out the getLevel() check in order to support the "new"
      // and "improved" serialization of dods sequences. 8/31/01 ndp
      //            if (getLevel() == 0)
      writeMarker(sink, START_OF_INSTANCE);

      for (BaseType baseType : allValue) {
        ClientIO bt = (ClientIO) baseType;
        bt.externalize(sink);
      }
    }
    // ************* Pulled out the getLevel() check in order to support the "new"
    // and "improved" serialization of dods sequences. 8/31/01 ndp
    //        if (getLevel() == 0)
    writeMarker(sink, END_OF_SEQUENCE);
  }
}
