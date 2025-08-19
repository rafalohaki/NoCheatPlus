/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.fail;

import java.util.LinkedList;
import java.util.List;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.Test;

import fr.neatmonster.nocheatplus.utilities.ColorUtil;

public class TestColorUtil {
	@Test
	public void testRemoveColor(){
		List<String[]> items = new LinkedList<String[]>();
		for (String[] item : new String[][]{
				{"", ""},
				{" ", " "},
				{"&", "&"},
				{"&&", "&&"},
				{"o3rg7cbo'!§)=?%V823rg7c", "o3rg7cbo'!§)=?%V823rg7c"},
		}){
			items.add(item);
		}
		String[][] generic = new String[][]{
				{"&/&/&/", ""},
				{"&/&/", ""},
				{" &/&/ ", "  "},
				{" &/&/", " "},
				{"&/", ""},
				{"&/ ", " "},
				{" &/", " "},
				{"123&/123", "123123"},
				
		};
		// Test with common color codes used in Minecraft
		char[] colorCodes = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 
		                     'a', 'b', 'c', 'd', 'e', 'f', 'k', 'l', 'm', 'n', 'o', 'r',
		                     'A', 'B', 'C', 'D', 'E', 'F', 'K', 'L', 'M', 'N', 'O', 'R'};
		for (char c : colorCodes){
			for (String[] pattern : generic){
				items.add(new String[]{ pattern[0].replace('/', c), pattern[1]});
			}
		}
		int i = 0;
		for (String[] item : items){
			String input = item[0];
			String expectedOutput = item[1];
			String detail = "no details.";
			String output = "(ERROR)"; 
			try{
				output = ColorUtil.removeColors(input);
			}
			catch(Throwable t){
				detail = t.getClass().getSimpleName() + ": " + t.getMessage();
			}
			if (!expectedOutput.equals(output)){
				fail("Wrong output at index " + i + " for input '" + input + "', expected '" + expectedOutput + "', but got instead: '" + output + "', details: " + detail );
			}
			i ++;
		}
	}
}
