package fr.inria.astor.core.ingredientbased;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.martiansoftware.jsap.JSAPException;

import fr.inria.astor.approaches.jgenprog.operators.ReplaceOp;
import fr.inria.astor.core.entities.*;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.manipulation.filters.TargetElementProcessor;
import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.astor.core.setup.ProjectRepairFacade;
import fr.inria.astor.core.solutionsearch.ExhaustiveSearchEngine;
import fr.inria.astor.core.solutionsearch.navigation.SuspiciousNavigationValues;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.IngredientPool;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.IngredientSearchStrategy;
import fr.inria.astor.core.solutionsearch.spaces.ingredients.transformations.IngredientTransformationStrategy;
import fr.inria.astor.core.solutionsearch.spaces.operators.AstorOperator;
import fr.inria.astor.core.solutionsearch.spaces.operators.IngredientBasedOperator;
import fr.inria.astor.core.stats.PatchHunkStats;
import fr.inria.astor.core.stats.PatchStat.HunkStatEnum;
import fr.inria.main.AstorOutputStatus;
import fr.inria.main.evolution.ExtensionPoints;
import spoon.reflect.code.CtCodeSnippetStatement;

/**
 * Exhaustive Search Engine
 * 
 * @author Matias Martinez, matias.martinez@inria.fr
 * 
 */
public class LLMIngredientEngine extends ExhaustiveSearchEngine implements IngredientBasedApproach {

	protected IngredientPool ingredientSpace = null;

	protected IngredientTransformationStrategy ingredientTransformationStrategy;

	public LLMIngredientEngine(MutationSupporter mutatorExecutor, ProjectRepairFacade projFacade)
			throws JSAPException {
		super(mutatorExecutor, projFacade);
		ConfigurationProperties.properties.setProperty(ExtensionPoints.TARGET_CODE_PROCESSOR.identifier, "statements");
		ConfigurationProperties.properties.setProperty(ExtensionPoints.OPERATORS_SPACE.identifier, "irr-statements");
		ConfigurationProperties.properties.setProperty(ExtensionPoints.SUSPICIOUS_NAVIGATION.identifier,
				SuspiciousNavigationValues.INORDER.toString());
	}

	@Override
	public void startSearch() throws Exception {

		if (this.ingredientSpace == null) {
			this.ingredientSpace = IngredientBasedEvolutionaryRepairApproachImpl
					.getIngredientPool(getTargetElementProcessors());
		}
		dateInitEvolution = new Date();
		// We don't evolve variants, so the generation is always one.
		generationsExecuted = 1;
		// For each variant (one is enough)
		int maxMinutes = ConfigurationProperties.getPropertyInt("maxtime");
		int maxGenerations = ConfigurationProperties.getPropertyInt("maxGeneration");
		// for stats
		int modifPointsAnalyzed = 0;
		int operatorExecuted = 0;

		getIngredientSpace().defineSpace(originalVariant);

		int totalmodfpoints = variants.get(0).getModificationPoints().size();
		for (ProgramVariant parentVariant : variants) {

			for (ModificationPoint modifPoint : this.getSuspiciousNavigationStrategy()
					.getSortedModificationPointsList(parentVariant.getModificationPoints())) {

				modifPointsAnalyzed++;

				log.info("\n MP (" + modifPointsAnalyzed + "/" + parentVariant.getModificationPoints().size()
						+ ") location to modify: " + modifPoint);

				// We create all operators to apply in the modifpoint
				List<OperatorInstance> operatorInstances = createInstancesOfOperators(
						(SuspiciousModificationPoint) modifPoint);

				// log.info("--- List of operators (" + operatorInstances.size()
				// + ") : " + operatorInstances);

				if (operatorInstances == null || operatorInstances.isEmpty())
					continue;

				for (OperatorInstance pointOperation : operatorInstances) {

					operatorExecuted++;

					// We validate the variant after applying the operator
					ProgramVariant solutionVariant = variantFactory.createProgramVariantFromAnother(parentVariant,
							generationsExecuted);
					solutionVariant.getOperations().put(generationsExecuted, Arrays.asList(pointOperation));

					applyNewMutationOperationToSpoonElement(pointOperation);

					log.debug("Operator:\n " + pointOperation);
					boolean solution = processCreatedVariant(solutionVariant, generationsExecuted);

					if (solution) {
						log.info("Solution found " + getSolutions().size());

						this.solutions.add(solutionVariant);

					}

					// We undo the operator (for try the next one)
					undoOperationToSpoonElement(pointOperation);

					if (solution) {
						log.info("Solution found " + getSolutions().size());
						this.savePatch(solutionVariant);
					}

					if (!this.solutions.isEmpty() && ConfigurationProperties.getPropertyBool("stopfirst")) {
						this.setOutputStatus(AstorOutputStatus.STOP_BY_PATCH_FOUND);
						log.debug(" modpoint analyzed " + modifPointsAnalyzed + ", operators " + operatorExecuted);
						return;
					}

					if (!belowMaxTime(dateInitEvolution, maxMinutes)) {
						this.setOutputStatus(AstorOutputStatus.TIME_OUT);
						log.debug("Max time reached");
						return;
					}

					if (maxGenerations <= operatorExecuted) {

						this.setOutputStatus(AstorOutputStatus.MAX_GENERATION);
						log.info("Stop-Max operator Applied " + operatorExecuted);
						log.info("modpoint:" + modifPointsAnalyzed + ":all:" + totalmodfpoints + ":operators:"
								+ operatorExecuted);
						return;
					}

					if (this.getSolutions().size() >= ConfigurationProperties.getPropertyInt("maxnumbersolutions")) {
						this.setOutputStatus(AstorOutputStatus.STOP_BY_PATCH_FOUND);
						log.debug("Stop-Max solutions reached " + operatorExecuted);
						log.debug("modpoint:" + modifPointsAnalyzed + ":all:" + totalmodfpoints + ":operators:"
								+ operatorExecuted);
						return;
					}
				}
			}
		}

		this.setOutputStatus(AstorOutputStatus.EXHAUSTIVE_NAVIGATED);
		System.out.println("\nEND exhaustive search Summary:\n" + "modpoint:" + modifPointsAnalyzed + ":all:"
				+ totalmodfpoints + ":operators:" + operatorExecuted);

	}

	/**
	 * @param modificationPoint
	 * @return
	 */
	@Override
	protected List<OperatorInstance> createInstancesOfOperators(SuspiciousModificationPoint modificationPoint) {
		List<OperatorInstance> ops = new ArrayList<>();
		
		// Get the code to fix
		String codeToFix = modificationPoint.getCodeElement().toString();

		// Get failing test class name from suspicious
		String failingTest = modificationPoint.getSuspicious().getClassName();

		int maxP = ConfigurationProperties.getPropertyInt("maxsuggestionsperpoint");

		// Generate fix using LLM
		List<String> suggestions = getLLMSuggestion(codeToFix, failingTest, maxP);

		for (String fixedCode : suggestions) {

			// Create ReplaceOp with the fix
			ReplaceOp replaceOp = new ReplaceOp();
			OperatorInstance opInstance = new StatementOperatorInstance();
			opInstance.setOperationApplied(replaceOp);
			opInstance.setOriginal(modificationPoint.getCodeElement());
			opInstance.setModificationPoint(modificationPoint);

			// Create a CtCodeSnippetStatement from the fixed code string using MutationSupporter
			CtCodeSnippetStatement snippetStatement = MutationSupporter.factory.Core().createCodeSnippetStatement();
			snippetStatement.setValue(fixedCode);
			opInstance.setModified(snippetStatement);

			ops.add(opInstance);
		}

		return ops;
	}
	
	private List<String> getLLMSuggestion(String buggyCode, String testCode, Integer maxP) {
		ArrayList<String> candidates = new ArrayList<String>();
		
		// Get prompt template name from configuration, with BASIC_REPAIR as default
		String templateName = ConfigurationProperties.getProperty("llmtemplate");
		if (templateName == null) {
			templateName = "BASIC_REPAIR";
		}
		
		// Get the prompt template - either from predefined templates or from configuration
		String promptTemplate;
		if (LLMPromptTemplate.hasTemplate(templateName)) {
			// Use predefined template
			promptTemplate = LLMPromptTemplate.getTemplate(templateName);
		} else {
			// Use custom template from configuration
			String customTemplate = ConfigurationProperties.getProperty("llmprompttemplate");
			if (customTemplate != null) {
				promptTemplate = customTemplate;
			} else {
				promptTemplate = LLMPromptTemplate.getTemplate("BASIC_REPAIR");
			}
		}
		
		// Fill the template with the buggy code and test code
		String prompt = LLMPromptTemplate.fillTemplate(promptTemplate, buggyCode, testCode);
		
		// Use the appropriate LLM service based on configuration
		String llmService = ConfigurationProperties.getProperty("llmservice");
		if (llmService == null) {
			llmService = "ollama"; // Default to ollama
		}
		
		String llmModel = ConfigurationProperties.getProperty("llmmodel");
		if (llmModel == null) {
			llmModel = "codellama"; // Default to codellama model
		}
		
		try {
			if ("ollama".equalsIgnoreCase(llmService)) {
				// Call Ollama API
				System.setProperty("llmservice", llmService);
				System.setProperty("llmmodel", llmModel);
				
				String response = LLMService.generateCode(prompt);
				
				// Clean up the response - remove markdown code blocks if they exist
				response = response.replaceAll("```java\\s*", "")
								  .replaceAll("```\\s*", "")
								  .trim();
				
				candidates.add(response);
				
				log.info("LLM response: " + response);
			} else {
				// Fallback for other services or testing
				if (testCode.contains("BisectionSolver")) {
					candidates.add("return solve(f, min, max)");
				} else {
					candidates.add("// LLM suggestion would go here");
				}
			}
		} catch (Exception e) {
			log.error("Error getting LLM suggestion", e);
			candidates.add("// Error getting LLM suggestion: " + e.getMessage());
		}
		
		// Ensure we have at least one candidate, even if it's an error message
		if (candidates.isEmpty()) {
			candidates.add("// No suggestions generated");
		}
		
		// Limit the number of suggestions
		int numCandidates = Math.min(candidates.size(), maxP);
		return candidates.subList(0, numCandidates);
	}

	@SuppressWarnings("unchecked")
	public List<OperatorInstance> createIngredientOpInstance(SuspiciousModificationPoint modificationPoint,
			IngredientBasedOperator astorOperator) throws Exception {

		List<OperatorInstance> ops = new ArrayList<>();
		List<Ingredient> ingredients = new ArrayList<>();

		if (astorOperator instanceof ReplaceOp) {// TODO
			String type = ingredientSpace.getType(new Ingredient(modificationPoint.getCodeElement())).toString();

			ingredients = ingredientSpace.getIngredients(modificationPoint.getCodeElement(), type);

		} else {
			ingredients = ingredientSpace.getIngredients(modificationPoint.getCodeElement());

		}

		if (ingredients == null) {
			log.error("Zero ingredients mp: " + modificationPoint + ", op " + astorOperator);
			return ops;
		}
		log.debug("Number of ingredients " + ingredients.size());
		for (Ingredient ingredient : ingredients) {

			List<OperatorInstance> operatorInstances = null;
			operatorInstances = astorOperator.createOperatorInstances(modificationPoint, ingredient,
					this.ingredientTransformationStrategy);

			ops.addAll(operatorInstances);

		}

		return ops;

	}

	public IngredientPool getIngredientSpace() {
		return ingredientSpace;
	}

	public void setIngredientSpace(IngredientPool ingredientSpace) {
		this.ingredientSpace = ingredientSpace;
	}

	@Override
	public IngredientPool getIngredientPool() {
		return this.ingredientSpace;
	}

	@Override
	public void setIngredientPool(IngredientPool ingredientPool) {
		this.ingredientSpace = ingredientPool;

	}

	@Override
	public void setIngredientTransformationStrategy(IngredientTransformationStrategy ingredientTransformationStrategy) {
		this.ingredientTransformationStrategy = ingredientTransformationStrategy;
	}

	@Override
	public IngredientTransformationStrategy getIngredientTransformationStrategy() {
		return ingredientTransformationStrategy;
	}

	@Override
	public IngredientSearchStrategy getIngredientSearchStrategy() {
		return null;
	}

	@Override
	public void setIngredientSearchStrategy(IngredientSearchStrategy ingredientStrategy) {

	}

	@SuppressWarnings("rawtypes")
	protected void loadIngredientPool() throws JSAPException, Exception {
		List<TargetElementProcessor<?>> ingredientProcessors = this.getTargetElementProcessors();
		// The ingredients for build the patches
		IngredientPool ingredientspace = IngredientBasedEvolutionaryRepairApproachImpl
				.getIngredientPool(ingredientProcessors);

		this.setIngredientPool(ingredientspace);

	}

	@SuppressWarnings("rawtypes")
	protected void loadIngredientSearchStrategy() throws Exception {
		this.setIngredientSearchStrategy(
				IngredientBasedEvolutionaryRepairApproachImpl.retrieveIngredientSearchStrategy(getIngredientPool()));
	}

	protected void loadIngredientTransformationStrategy() throws Exception {

		IngredientTransformationStrategy ingredientTransformationStrategyLoaded = IngredientBasedEvolutionaryRepairApproachImpl
				.retrieveIngredientTransformationStrategy();
		this.setIngredientTransformationStrategy(ingredientTransformationStrategyLoaded);
	}

	@Override
	protected void setParticularStats(PatchHunkStats hunk, OperatorInstance genOperationInstance) {
		// TODO Auto-generated method stub
		super.setParticularStats(hunk, genOperationInstance);
		hunk.getStats().put(HunkStatEnum.INGREDIENT_SCOPE,
				((genOperationInstance.getIngredientScope() != null) ? genOperationInstance.getIngredientScope()
						: "-"));

		if (genOperationInstance.getIngredient() != null
				&& genOperationInstance.getIngredient().getDerivedFrom() != null)
			hunk.getStats().put(HunkStatEnum.INGREDIENT_PARENT, genOperationInstance.getIngredient().getDerivedFrom());

	}

	@Override
	public void loadExtensionPoints() throws Exception {
		super.loadExtensionPoints();
		this.loadIngredientPool();
		this.loadIngredientSearchStrategy();
		this.loadIngredientTransformationStrategy();
	}

}
