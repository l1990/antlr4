/*
 * [The "BSD license"]
 *  Copyright (c) 2012 Terence Parr
 *  Copyright (c) 2012 Sam Harwell
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.antlr.v4.test.tool;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/** Test parser execution.
 *
 *  For the non-greedy stuff, the rule is that .* or any other non-greedy loop
 *  (any + or * loop that has an alternative with '.' in it is automatically
 *  non-greedy) never sees past the end of the rule containing that loop.
 *  There is no automatic way to detect when the exit branch of a non-greedy
 *  loop has seen enough input to determine how much the loop should consume
 *  yet still allow matching the entire input. Of course, this is extremely
 *  inefficient, particularly for things like
 *
 *     block : '{' (block|.)* '}' ;
 *
 *  that need only see one symbol to know when it hits a '}'. So, I
 *  came up with a practical solution.  During prediction, the ATN
 *  simulator never fall off the end of a rule to compute the global
 *  FOLLOW. Instead, we terminate the loop, choosing the exit branch.
 *  Otherwise, we predict to reenter the loop.  For example, input
 *  "{ foo }" will allow the loop to match foo, but that's it. During
 *  prediction, the ATN simulator will see that '}' reaches the end of a
 *  rule that contains a non-greedy loop and stop prediction. It will choose
 *  the exit branch of the inner loop. So, the way in which you construct
 *  the rule containing a non-greedy loop dictates how far it will scan ahead.
 *  Include everything after the non-greedy loop that you know it must scan
 *  in order to properly make a prediction decision. these beasts are tricky,
 *  so be careful. don't liberally sprinkle them around your code.
 *
 *  To simulate filter mode, use ( .* (pattern1|pattern2|...) )*
 *
 *  Nongreedy loops match as much input as possible while still allowing
 *  the remaining input to match.
 */
public class TestParserExec extends BaseTest {


	/**
	 * This is a regression test for antlr/antlr4#118.
	 * https://github.com/antlr/antlr4/issues/118
	 */
	@Ignore("Performance impact of passing this test may not be worthwhile")
	// TODO: port to test framework (not ported because test currently fails)
	@Test public void testStartRuleWithoutEOF() {
		String grammar =
			"grammar T;\n"+
			"s @after {dumpDFA();}\n" +
			"  : ID | ID INT ID ;\n" +
			"ID : 'a'..'z'+ ;\n"+
			"INT : '0'..'9'+ ;\n"+
			"WS : (' '|'\\t'|'\\n')+ -> skip ;\n";
		String result = execParser("T.g4", grammar, "TParser", "TLexer", "s",
								   "abc 34", true);
		String expecting =
			"Decision 0:\n" +
			"s0-ID->s1\n" +
			"s1-INT->s2\n" +
			"s2-EOF->:s3=>1\n"; // Must point at accept state
		assertEquals(expecting, result);
		assertNull(this.stderrDuringParse);
	}

	/**
	 * This is a regression test for antlr/antlr4#588 "ClassCastException during
	 * semantic predicate handling".
	 * https://github.com/antlr/antlr4/issues/588
	 */
	// TODO: port to test framework (can we simplify the Psl grammar?)
	@Test public void testFailedPredicateExceptionState() throws Exception {
		String grammar = load("Psl.g4", "UTF-8");
		String found = execParser("Psl.g4", grammar, "PslParser", "PslLexer", "floating_constant", " . 234", false);
		assertEquals("", found);
		assertEquals("line 1:6 rule floating_constant DEC:A floating-point constant cannot have internal white space\n", stderrDuringParse);
	}


	/**
	 * This is a regression test for antlr/antlr4#672 "Initialization failed in
	 * locals".
	 * https://github.com/antlr/antlr4/issues/672
	 */
	// TODO: port to test framework (missing templates)
	@Test public void testAttributeValueInitialization() throws Exception {
		String grammar =
			"grammar Data; \n" +
			"\n" +
			"file : group+ EOF; \n" +
			"\n" +
			"group: INT sequence {System.out.println($sequence.values.size());} ; \n" +
			"\n" +
			"sequence returns [List<Integer> values = new ArrayList<Integer>()] \n" +
			"  locals[List<Integer> localValues = new ArrayList<Integer>()]\n" +
			"         : (INT {$localValues.add($INT.int);})* {$values.addAll($localValues);}\n" +
			"; \n" +
			"\n" +
			"INT : [0-9]+ ; // match integers \n" +
			"WS : [ \\t\\n\\r]+ -> skip ; // toss out all whitespace\n";

		String input = "2 9 10 3 1 2 3";
		String found = execParser("Data.g4", grammar, "DataParser", "DataLexer", "file", input, false);
		assertEquals("6\n", found);
		assertNull(stderrDuringParse);
	}
}
