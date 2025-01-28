import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The type Changelog generator.
 */
public class ChangelogGenerator
{
    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args)
    {
        String inputFilePath = "changelog.txt";
        String outputFilePath = "formatted_changelog.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath)))
        {

            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null)
            {   lines.add(line);
            }

            int lastDashIndex = -1;
            for (int i = lines.size() - 1; i >= 0; i--)
            {
                if (lines.get(i).trim().matches("^-+$"))
                {   // Check for line full of dashes
                    lastDashIndex = i;
                    break;
                }
            }

            writer.write("<div style=\"background-color: #212121; color: #aaafb6; font-family: 'JetBrains Mono', monospace; font-size: 9.8pt;\">");
            writer.write("<pre>");

            for (int i = lastDashIndex + 1; i < lines.size(); i++)
            {
                line = lines.get(i);
                String lineText = line.trim();
                boolean isMajorWarning = lineText.startsWith("!!");
                boolean isWarning = !isMajorWarning && lineText.startsWith("!");
                boolean isImportant = lineText.startsWith("*");
                boolean isListElement = lineText.startsWith("-") || isImportant || isMajorWarning || isWarning;
                boolean isSectionTitle = !isListElement && lineText.endsWith(":");
                boolean isGroupTitle = !isListElement && lineText.endsWith(":-");
                boolean isTitleLine = !isListElement && !isSectionTitle && !isGroupTitle;

                if (isTitleLine)
                {   line = "<span style=\"font-size: 18px; color: #ffffff; font-weight: bold;\">" + line + "</span>";
                }
                else if (isGroupTitle)
                {   line = "<strong style=\"font-size: 13px; color: #1A32CD;\"><u>" + line.substring(0, line.length() - 2) + "</u></strong>";
                }
                else if (isSectionTitle)
                {   line = "<u><strong style=\"font-size: 14px; color: #ffffff;\">" + line + "</strong></u>";
                }
                else if (isMajorWarning)
                {   line = "<span style=\"color: #ff4d49;\">" + line + "</span>";
                }
                else if (isWarning)
                {   line = "<span style=\"color: #ff9900;\">" + line + "</span>";
                }
                else if (isImportant)
                {   line = "<strong style=\"color: #ffffff;\">" + line + "</strong>";
                }

                writer.write(line + "<br />");
            }

            writer.write("</pre></div>");
            writer.write("</body></html>");

        }
        catch (IOException e)
        {   System.err.println("Error processing the files: " + e.getMessage());
        }
    }
}

/* Here is the formatting for the changelog, in case you need it:

The very first line is the title of the changelog (i.e. "2.3.6"). Font size will be set to 18 and bolded.
        ":" at the end of a line (that isn't a list element) indicates a section. Font size will be set to 14 and bolded.
        ":-" at the end of a line (that isn't a list element) indicates a group or subsection. Font size will be set to 13, bolded, and colored blue.

        "-" at the beginning of a line indicates a list element.
        "*" at the beginning of a line indicates an important list element. Will be bolded.
        "!" at the beginning of a line indicates a warning list element. Will be colored orange.
        "!!" at the beginning of a line indicates a major warning list element. Will be colored red.

        Example changelog:
        2.3
        !! This is a major warning! I usually put these at the top if necessary

        Main section 1:

        Group 1:-
        - Added a new feature
        - Here is a description of the feature. The formatter respects indentation.
        * Here is an important feature!
        ! But watch out for this thing!

        Main section 2:

        Group 1:-
        * This thing was changed
        - This thing also changed, but isn't as important

                                          Group 2:-
                                          - Another thing happened here


                                          Result:
                                          Image

 */

/// credits go to https://github.com/Momo-Softworks/Cold-Sweat for the changelog generator I got perms to use it from them.