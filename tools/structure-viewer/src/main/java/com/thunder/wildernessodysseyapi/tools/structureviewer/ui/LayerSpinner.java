package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import java.text.ParseException;

/** Displays the existing -1 layer sentinel as "All" without changing mesh or preference semantics. */
final class LayerSpinner extends JSpinner {
    LayerSpinner() {
        super(new SpinnerNumberModel(-1, -1, 0, 1));
        setToolTipText("All shows the whole structure. Choose a Y layer to see one floor.");
        getAccessibleContext().setAccessibleName("Y layer");
        installEditor();
    }

    // The layer range is replaced after an import; preserve the readable editor on the new model.
    @Override public void setModel(SpinnerModel model) {
        super.setModel(model);
        if (getEditor() != null) installEditor();
    }

    private void installEditor() {
        DefaultEditor editor = new DefaultEditor(this);
        JFormattedTextField field = editor.getTextField();
        field.setColumns(3);
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setFormatterFactory(new DefaultFormatterFactory(new JFormattedTextField.AbstractFormatter() {
            @Override public Object stringToValue(String text) throws ParseException {
                if (text.strip().equalsIgnoreCase("All")) return -1;
                try {
                    int value = Integer.parseInt(text.strip());
                    int maximum = ((Number)((SpinnerNumberModel)getModel()).getMaximum()).intValue();
                    if (value < -1 || value > maximum) throw new NumberFormatException();
                    return value;
                } catch (NumberFormatException error) {
                    throw new ParseException("Choose All or a valid Y layer.", 0);
                }
            }
            @Override public String valueToString(Object value) {
                return ((Number)value).intValue() == -1 ? "All" : value.toString();
            }
        }));
        field.setValue(getValue());
        setEditor(editor);
    }
}
