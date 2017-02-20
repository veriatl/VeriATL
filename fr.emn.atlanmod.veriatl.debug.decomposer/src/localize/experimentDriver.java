package localize;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;
import org.eclipse.m2m.atl.common.ATL.MatchedRule;
import org.eclipse.m2m.atl.common.OCL.OclExpression;
import org.eclipse.m2m.atl.emftvm.ExecEnv;

import contract.ContractLoader;
import contract.Elimination;
import contract.Introduction;
import datastructure.ContextEntry;
import datastructure.Node;
import datastructure.NodeHelper;
import datastructure.ProveOption;
import fr.emn.atlanmod.atl2boogie.xtend.core.driver;
import fr.emn.atlanmod.atl2boogie.xtend.lib.URIs;
import fr.emn.atlanmod.atl2boogie.xtend.lib.atl;
import fr.emn.atlanmod.atl2boogie.xtend.ocl.ocl2boogie;
import fr.emn.atlanmod.atl2boogie.xtend.util.CompilerConstants;
import transformation.GenBy;
import transformation.Trace;
import transformation.TransformationLoader;

public class experimentDriver {
	
	static public String SINGLE ="single";
	static public String PLUSONE ="plusone";
	static public String INC ="incremental";
	
	
	//TODO should get rid of this, if we not interested in print full driver with full post.
	static  public HashMap<String, String> postsStrings = new HashMap<String, String>();
	static  public HashMap<String, HashSet<String>> postsTrace = new HashMap<String, HashSet<String>>();
	static  public HashMap<String, Integer> postsTime = new HashMap<String, Integer>();
	static public ArrayList<String> posts = new ArrayList<String>();
	
	// Notice: Termination of this strategy depends on what rules are selected, e.g. if inclusion elimin 1 and 2 are both presented, of course it will loop forever
	// it also depends on whether a fix-point can be reached at each stage.
	
	// the limitation of this strategy is that it doesn't handle well if eliminate triggers new introduction, which is we trying to avoid because of mutual recursive call.
	
	// the most difficulty part is type inference, and deep copy of objects.
	@SuppressWarnings("unchecked")
	public static void decompose(URI atl, URI emftvm, String moduleName, URI src, URI trg, URI contract, URI outputPath) throws Exception {
		long start = System.currentTimeMillis();

		

		// initialization
		driver.doEMFSetup();
		driver.doVeriATLSetup(atl, src, trg, contract);
		ExecEnv env = Trace.moduleLoader(emftvm.trimFileExtension().trimSegments(1), moduleName, src, trg, driver.srcmm.getName(), driver.trgmm.getName());
		Map<String, ArrayList<String>> trace = Trace.getTrace(driver.trgmm, env);
		List<OclExpression> postconditions = ContractLoader.init(driver.contract_resource);
		List<MatchedRule> rules = TransformationLoader.init(driver.atl_resource);
		
		
		
		
		// clean sub-goals previously generated
		URIs.delete(outputPath);
		
		
		// proof strategy starts
		for (OclExpression post : postconditions) {
			
			ArrayList<Node> tree = new ArrayList<Node>();
			Introduction.init(env, trace, tree, driver.trgmm);
			
			HashMap<EObject, ContextEntry> emptyTrace = new HashMap<EObject, ContextEntry>();
			Node root = new Node(0, post, null, emptyTrace, null, null);
			tree.add(root);
			
			
			//intro
			ArrayList<Node> oldLeafs;
			ArrayList<Node> newLeafs;
			
			do{
				oldLeafs = NodeHelper.findLeafs(tree);
				
				for(Node n : oldLeafs){
					//TODO, default prove option
					Introduction.introduction(n, n.getContent(), n.getContext(), n.getLevel(), ProveOption.EACH);	
				}
				
				newLeafs = NodeHelper.findLeafs(tree);
			}while(!oldLeafs.containsAll(newLeafs));
			

			//elimin
			Elimination.init(env, trace, tree, driver.trgmm);
			while(!Elimination.terminated(NodeHelper.findLeafs(tree))){
				ArrayList<Node> leafs = NodeHelper.findLeafs(tree);
				
				for(Node n : leafs){
					HashMap<EObject, ContextEntry> ctx = n.getContext();
					for(EObject ocl : ctx.keySet()){
						if(ctx.get(ocl).isEliminated()){
							continue;
						}else{
							ctx.get(ocl).setEliminated(true);
							Elimination.elimin(n, ocl);	
							break;
						}
					}
				}

			}
						
			Collections.sort(tree);

			String goalName = post.getCommentsBefore().get(0).replace("--", "");	
			URI output = outputPath.appendSegment(experimentDriver.SINGLE);

			// Print single postcondition in its consice presentation.
			String org = prtingFastDriver(env, post, NodeHelper.findLeafs(tree), goalName);
			driver.generateBoogieFile(output, goalName, CompilerConstants.BOOGIE_EXT, org);	
			
			
			
			

		}
		
		String org = prtingFullDriver(env);
		driver.generateBoogieFile(outputPath, CompilerConstants.FULL, CompilerConstants.BOOGIE_EXT, org);
			
		//combinePlusOne(outputPath);
		
		loadVerificationTime(outputPath);
		inc(outputPath, 1);
		
		//subsumed(outputPath, 4);
		//subsumed(outputPath, 3);
		//subsumed(outputPath, 2);
		
		
		long end = System.currentTimeMillis();
		System.out.println(end-start);
		
		
	}

	/**
	 * find an initial post (Post_i) with trace size `n`, then find any other posts (Post_s1 .. Post_sx) whose trace that are subsumed by the initial post
	 * Finally, incrementally build VCs = Post_i && Post_s1 && ... && Post_sx, where Post_s1 to Post_sx are ordered by their verification time.
	 * 
	 * Aim: 
	 * 	- if subsumes, is it always worthwhile to combine posts; 
	 *  - when we have to chop into two sets of post, because combined posts is too difficult to solve.
	 * */
	private static void subsumed(URI outputPath, int n ) {
		
		ArrayList<String> nRuleTrace = new ArrayList<String>();
		
		// find initial post with trace of size `n`
		for(String post : posts) {
			HashSet<String> postTrace = postsTrace.get(post);
			if(postTrace.size() == n) {
				nRuleTrace.add(post);
			}
			
		}
		
		HashMap<String, HashSet<String>> subsumes = new HashMap<String, HashSet<String>>();
		
		// initialize subsumes
		for(String post : nRuleTrace) {
			subsumes.put(post, new HashSet<String>());			
		}
		
		// find subsumes
		for(String post : nRuleTrace) {
			HashSet<String> postTrace = postsTrace.get(post);
			
			for(String rest : posts){
				HashSet<String> restTrace = postsTrace.get(rest);
				Set<String> intersection = new HashSet<String>(restTrace); 
				intersection.retainAll(postTrace);
				
				if(intersection.size() == restTrace.size() && !post.equals(rest)){
					subsumes.get(post).add(rest);
				}			
			}
	
		}
		
		
		for(String post : nRuleTrace) {
			URI output = outputPath.appendSegment("subsumes").appendSegment(n+"-"+post);
			ArrayList<String> incCase = new ArrayList<String>();
			incCase.add(post);
			
			ArrayList<String> subs = new ArrayList<String>(subsumes.get(post));
			Collections.sort(subs, new Comparator<String>(){
			    public int compare(String s1, String s2){
			        return postsTime.get(s1) - postsTime.get(s2);
			    }

			});
			
			for(String sub : subs){
				incCase.add(sub);
				String content = genContent(incCase);
				String fileName = String.format("%03d", incCase.size());
				driver.generateBoogieFile(output, fileName, CompilerConstants.BOOGIE_EXT, content);
			}	
			
		}
	
	}
	
	/**
	 * 
	 * */
	private static void inc(URI outputPath, int n ) {
		
		ArrayList<String> nRuleTrace = new ArrayList<String>();
		
		for(String post : posts) {
			HashSet<String> postTrace = postsTrace.get(post);
			if(postTrace.size() <= n) {
				nRuleTrace.add(post);
			}
			
		}
		
		ArrayList<String> incCase = new ArrayList<String>();
		URI output = outputPath.appendSegment(experimentDriver.INC);
		
		ArrayList<String> subs = new ArrayList<String>(nRuleTrace);
		Collections.sort(subs, new Comparator<String>(){
		    public int compare(String s1, String s2){
		        return postsTime.get(s1) - postsTime.get(s2);
		    }

		});
		
		for(String post : subs) {
			incCase.add(post);
			String content = genContent(incCase);
			String fileName = String.format("%03d", incCase.size());
			driver.generateBoogieFile(output, fileName, CompilerConstants.BOOGIE_EXT, content);
		}
		
	}
	
	
	private static String genContent(ArrayList<String> incCase) {
		HashSet<String> involvedRules = new HashSet<String>();
		
		for(String post : incCase) {
			involvedRules.addAll(postsTrace.get(post));
		}
		
		
		String res="";
		res += printDriverHeader();
		
		for(String r : involvedRules){
			res += String.format("call %s_matchAll();\n", r);
		}
		for(String r : involvedRules){
			res += String.format("call %s_applyAll();\n", r);
		}
		
		res += "\n";
		

		res += "\n";
		
		// print postconditions
		for(String post : incCase) {
			res += String.format("// %s : %s\n", post, postsTime.get(post));
			res += postsStrings.get(post);
			res += "\n";
		}
		
		
		
		res += printDriverFooter();
		
		return res;
	}

	/**
	 * 
	 * */
	private static void combinePlusOne(URI outputPath) {
		for(String post : posts) {
			HashSet<String> postTrace = postsTrace.get(post);
			String postString = postsStrings.get(post);
			
			for(String anotherPost : posts.subList(posts.indexOf(post)+1, posts.size())) {
				
				String fileName = String.format("%s-%s", post, anotherPost);
				URI output = outputPath.appendSegment(experimentDriver.PLUSONE);
				
				HashSet<String> anotherPostTrace = postsTrace.get(anotherPost);
				String anotherPostString = postsStrings.get(anotherPost);
				
				HashSet<String> involvedRules = new HashSet<String>();
				involvedRules.addAll(postTrace);
				involvedRules.addAll(anotherPostTrace);
				
				String res="";
				res += printDriverHeader();
				
				for(String r : involvedRules){
					res += String.format("call %s_matchAll();\n", r);
				}
				for(String r : involvedRules){
					res += String.format("call %s_applyAll();\n", r);
				}
				
				res += "\n";
				
				Set<String> intersection = new HashSet<String>(postTrace); // use the copy constructor
				intersection.retainAll(anotherPostTrace);
				
				// print intersections
				res += "//";
				res += "//";
				for(String s : intersection) {
					res += String.format("%s,", s);
				}
				res += "\n";
				
				// print postconditions
				res += postString;
				res += "\n";
				res += anotherPostString;
				res += "\n";
				
				res += printDriverFooter();
				
				
				fileName = String.format("%s-%03d_%03d_%03d", fileName, postTrace.size(), anotherPostTrace.size(), intersection.size());
				
				driver.generateBoogieFile(output, fileName, CompilerConstants.BOOGIE_EXT, res);
			}
		}
	}
	
	private static String prtingFullDriver(ExecEnv env) {
		String res="";
		res += printDriverHeader();
		
		for(org.eclipse.m2m.atl.emftvm.Rule r : env.getRules()){
			res += String.format("call %s_matchAll();\n", r.getName());
		}
		for(org.eclipse.m2m.atl.emftvm.Rule r : env.getRules()){
			res += String.format("call %s_applyAll();\n", r.getName());
		}

		for(String postName : postsStrings.keySet()){
			res += String.format("// %s \n", postName);
			res += postsStrings.get(postName);
		}
		
		
		res += printDriverFooter();
		return res;
	}

	private static String prtingFastDriver(ExecEnv env, OclExpression post, ArrayList<Node> leafs, String goal)  {		
		String res="";
		res += printDriverHeader();
		
		HashSet<String> involvedRules = new HashSet<String>();
		for(Node n : leafs){
			involvedRules.addAll(n.getInvolvedRuls());	
		}
		
		for(String r : involvedRules){
			res += String.format("call %s_matchAll();\n", r);
		}
		for(String r : involvedRules){
			res += String.format("call %s_applyAll();\n", r);
		}
		res += "\n";
		res += String.format("//%s \n", goal);
		res += String.format("// rule size -> %d\n", involvedRules.size());
		res += printPost(post);
		res += printDriverFooter();
		
		postsStrings.put(goal, printPost(post));
		postsTrace.put(goal, involvedRules);
		posts.add(goal);
		
		return res;
	}
	




	private static String printPost(OclExpression post) {
		return String.format("assert %s;\n", ocl2boogie.genOclExpression(post, atl.genTrgHeap()));		
	}

	private static String printDriverHeader() {
		String res = "";
		res += "implementation driver(){\n";
		res += "call init_tar_model();\n";	
		return res;
	}
	
	private static String printDriverFooter() {
		String res = "}\n";
		return res;
	}
	
	
	private static void loadVerificationTime(URI output) throws IOException{
		URI res = output.trimSegments(1).appendSegment("Res").appendSegment("single").appendFileExtension("txt");
		URIConverter uriConverter = new ExtensibleURIConverterImpl();
				
		InputStream is = uriConverter.createInputStream(res);
		List<String> doc = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines().collect(Collectors.toList());	
		
		for(String ln : doc){
			String[] t = ln.split(",");
			if(t.length == 3){
				String id = t[0];
				String time = t[2].replace("Time:", "").trim();
				id = id.replace("Id:UML-", "").replace(".bpl", "").trim();
				postsTime.put(id, Integer.parseInt(time));
			}
		}
		
		
		
		
	}
	
	
	public static void clean(){
		
	}
}
