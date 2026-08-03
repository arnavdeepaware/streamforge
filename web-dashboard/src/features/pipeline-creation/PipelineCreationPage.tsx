import { useReducer, useState, type Dispatch } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type {
  ApiFieldViolation,
  JsonObject,
  PipelineConfiguration,
} from '../../api/controlPlaneClient';
import {
  controlPlaneQueryKeys,
  usePipelineCreation,
  usePipelineValidation,
} from '../../api/queries';
import { PageHeader } from '../../components/PageHeader';
import { TextField, WizardSteps } from './WizardControls';

const steps = [
  'Input type',
  'Configure input',
  'Transformations',
  'Output blueprint',
  'Output sink',
  'Validate and review',
  'Save pipeline',
] as const;

type TransformationPreset = 'RENAME_SYMBOL' | 'ADD_PIPELINE_LABEL';
type BlueprintPreset = 'CANONICAL_EVENT';

type PipelineDraft = {
  name: string;
  description: string;
  inputPath: string;
  source: string;
  venue: string;
  maximumFrameSize: string;
  transformation: TransformationPreset;
  blueprint: BlueprintPreset;
  outputPath: string;
};

type WizardState = {
  currentStep: number;
  draft: PipelineDraft;
  fieldErrors: ApiFieldViolation[];
  validationMessage: string | null;
};

type WizardAction =
  | { type: 'field'; field: keyof PipelineDraft; value: string }
  | { type: 'step'; step: number }
  | { type: 'errors'; errors: ApiFieldViolation[] }
  | { type: 'validation-message'; message: string | null }
  | { type: 'import'; draft: PipelineDraft };

const initialDraft: PipelineDraft = {
  name: '',
  description: '',
  inputPath: 'fixtures/ticks.stp',
  source: 'simulator/local',
  venue: 'XNAS',
  maximumFrameSize: '49',
  transformation: 'RENAME_SYMBOL',
  blueprint: 'CANONICAL_EVENT',
  outputPath: 'output/events.jsonl',
};

const initialState: WizardState = {
  currentStep: 0,
  draft: initialDraft,
  fieldErrors: [],
  validationMessage: null,
};

export function PipelineCreationPage() {
  const [state, dispatch] = useReducer(reducer, initialState);
  const [importText, setImportText] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const validate = usePipelineValidation();
  const create = usePipelineCreation();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const configuration = toConfiguration(state.draft);
  const currentStep = steps[state.currentStep];

  function setField(field: keyof PipelineDraft, value: string) {
    dispatch({ type: 'field', field, value });
  }

  async function validateConfiguration() {
    dispatch({ type: 'errors', errors: [] });
    dispatch({ type: 'validation-message', message: null });
    try {
      const result = await validate.mutateAsync(configuration);
      dispatch({ type: 'errors', errors: result.errors });
      dispatch({
        type: 'validation-message',
        message: result.valid
          ? 'Configuration is valid and ready to save.'
          : null,
      });
    } catch (error) {
      const errors = fieldErrors(error);
      dispatch({ type: 'errors', errors });
      dispatch({ type: 'validation-message', message: errorMessage(error) });
      moveToFirstError(errors, dispatch);
    }
  }

  async function savePipeline() {
    dispatch({ type: 'errors', errors: [] });
    dispatch({ type: 'validation-message', message: null });
    try {
      const saved = await create.mutateAsync({
        name: state.draft.name,
        description: state.draft.description,
        configuration,
      });
      await queryClient.invalidateQueries({
        queryKey: controlPlaneQueryKeys.pipelines,
      });
      navigate('/pipelines');
      return saved;
    } catch (error) {
      const errors = fieldErrors(error);
      dispatch({ type: 'errors', errors });
      dispatch({ type: 'validation-message', message: errorMessage(error) });
      moveToFirstError(errors, dispatch);
    }
  }

  function importConfiguration() {
    try {
      dispatch({ type: 'import', draft: parseImportedDraft(importText) });
      setImportError(null);
      dispatch({
        type: 'validation-message',
        message: 'Configuration imported. Validate it before saving.',
      });
    } catch (error) {
      setImportError(errorMessage(error));
    }
  }

  return (
    <section
      className="page-content pipeline-wizard"
      aria-labelledby="page-title"
    >
      <PageHeader eyebrow="Control plane" title="New Pipeline">
        Configure a finite local pipeline using only typed, declarative
        settings.
      </PageHeader>
      <WizardSteps
        currentStep={state.currentStep}
        onSelect={(step) => dispatch({ type: 'step', step })}
        steps={steps}
      />
      <section className="wizard-panel" aria-labelledby="wizard-step-title">
        <p className="eyebrow">
          Step {state.currentStep + 1} of {steps.length}
        </p>
        <h3 id="wizard-step-title">{currentStep}</h3>
        {currentStep === 'Input type' ? <InputTypeStep /> : null}
        {currentStep === 'Configure input' ? (
          <InputConfigurationStep
            draft={state.draft}
            errors={state.fieldErrors}
            onChange={setField}
          />
        ) : null}
        {currentStep === 'Transformations' ? (
          <TransformationStep draft={state.draft} onChange={setField} />
        ) : null}
        {currentStep === 'Output blueprint' ? <BlueprintStep /> : null}
        {currentStep === 'Output sink' ? (
          <OutputStep
            draft={state.draft}
            errors={state.fieldErrors}
            onChange={setField}
          />
        ) : null}
        {currentStep === 'Validate and review' ? (
          <ReviewStep
            configuration={configuration}
            draft={state.draft}
            errors={state.fieldErrors}
            importError={importError}
            importText={importText}
            onImport={importConfiguration}
            onImportTextChange={setImportText}
            onValidate={() => void validateConfiguration()}
            validationMessage={state.validationMessage}
            validating={validate.isPending}
          />
        ) : null}
        {currentStep === 'Save pipeline' ? (
          <SaveStep
            draft={state.draft}
            errors={state.fieldErrors}
            onChange={setField}
            onSave={() => void savePipeline()}
            saving={create.isPending}
            validationMessage={state.validationMessage}
          />
        ) : null}
      </section>
      <div className="wizard-actions">
        <button
          disabled={state.currentStep === 0}
          onClick={() =>
            dispatch({ type: 'step', step: state.currentStep - 1 })
          }
          type="button"
        >
          Back
        </button>
        {state.currentStep < steps.length - 1 ? (
          <button
            onClick={() =>
              dispatch({ type: 'step', step: state.currentStep + 1 })
            }
            type="button"
          >
            Continue
          </button>
        ) : null}
      </div>
    </section>
  );
}

function InputTypeStep() {
  return (
    <fieldset>
      <legend>Select the source format</legend>
      <label className="choice-card">
        <input
          aria-label="STP binary"
          checked
          name="input-type"
          readOnly
          type="radio"
          value="STP_BINARY"
        />
        <span>
          <strong>STP binary</strong>
          <small>
            Use the bounded educational Simple Tick Protocol v1 file input.
          </small>
        </span>
      </label>
      <p className="form-hint">
        The initial guided flow supports STP binary input. JSONL and CSV remain
        available through saved configuration files while their guided forms are
        designed.
      </p>
    </fieldset>
  );
}

type StepFieldsProps = {
  draft: PipelineDraft;
  errors: ApiFieldViolation[];
  onChange: (field: keyof PipelineDraft, value: string) => void;
};

function InputConfigurationStep({ draft, errors, onChange }: StepFieldsProps) {
  return (
    <div className="field-grid">
      <TextField
        label="STP file path"
        name="input-path"
        value={draft.inputPath}
        onChange={(value) => onChange('inputPath', value)}
        error={errorFor(errors, 'input.path')}
      />
      <TextField
        label="Source identity"
        name="source"
        value={draft.source}
        onChange={(value) => onChange('source', value)}
        error={errorFor(errors, 'input.source')}
      />
      <TextField
        label="Venue"
        name="venue"
        value={draft.venue}
        onChange={(value) => onChange('venue', value)}
        error={errorFor(errors, 'input.venue')}
      />
      <TextField
        label="Maximum STP frame size"
        name="maximum-frame-size"
        inputMode="numeric"
        value={draft.maximumFrameSize}
        onChange={(value) => onChange('maximumFrameSize', value)}
        error={errorFor(errors, 'input.maximumFrameSize')}
      />
    </div>
  );
}

function TransformationStep({
  draft,
  onChange,
}: Pick<StepFieldsProps, 'draft' | 'onChange'>) {
  return (
    <fieldset>
      <legend>Choose safe transformation rules</legend>
      <label className="choice-card">
        <input
          aria-label="Add pipeline label"
          checked={draft.transformation === 'ADD_PIPELINE_LABEL'}
          name="transformation"
          onChange={() => onChange('transformation', 'ADD_PIPELINE_LABEL')}
          type="radio"
        />
        <span>
          <strong>Add pipeline label</strong>
          <small>
            Adds the typed `pipelineLabel` field to the transformed document.
            The canonical JSONL blueprint remains unchanged.
          </small>
        </span>
      </label>
      <label className="choice-card">
        <input
          aria-label="Rename symbol to ticker"
          checked={draft.transformation === 'RENAME_SYMBOL'}
          name="transformation"
          onChange={() => onChange('transformation', 'RENAME_SYMBOL')}
          type="radio"
        />
        <span>
          <strong>Rename symbol to ticker</strong>
          <small>
            Applies the typed `rename` operation from `instrument.symbol` to
            `instrument.ticker`.
          </small>
        </span>
      </label>
      <p className="form-hint">
        The dashboard only emits the versioned transformation AST. It does not
        accept executable expressions, scripts, or templates.
      </p>
    </fieldset>
  );
}

function BlueprintStep() {
  return (
    <fieldset>
      <legend>Choose the nested output structure</legend>
      <label className="choice-card">
        <input
          aria-label="Canonical event blueprint"
          checked
          name="blueprint"
          readOnly
          type="radio"
        />
        <span>
          <strong>Canonical event</strong>
          <small>
            Writes the event ID, event type, source, and sequence number as a
            compact JSON object.
          </small>
        </span>
      </label>
      <p className="form-hint">
        Blueprint references are validated by the backend. This guided preset
        contains no templating or executable code.
      </p>
    </fieldset>
  );
}

function OutputStep({ draft, errors, onChange }: StepFieldsProps) {
  return (
    <div className="field-grid">
      <fieldset className="read-only-field">
        <legend>Output type</legend>
        <label>
          <input
            aria-label="JSON Lines output"
            checked
            name="output-type"
            readOnly
            type="radio"
          />{' '}
          JSON Lines
        </label>
      </fieldset>
      <TextField
        label="JSONL output path"
        name="output-path"
        value={draft.outputPath}
        onChange={(value) => onChange('outputPath', value)}
        error={errorFor(errors, 'output.path')}
      />
    </div>
  );
}

type ReviewStepProps = {
  configuration: PipelineConfiguration;
  draft: PipelineDraft;
  errors: ApiFieldViolation[];
  importError: string | null;
  importText: string;
  onImport: () => void;
  onImportTextChange: (value: string) => void;
  onValidate: () => void;
  validationMessage: string | null;
  validating: boolean;
};

function ReviewStep(props: ReviewStepProps) {
  const exported = JSON.stringify(
    {
      name: props.draft.name,
      description: props.draft.description,
      configuration: props.configuration,
    },
    null,
    2,
  );
  return (
    <div className="review-stack">
      <p>
        Review this declarative configuration before it is sent to the control
        plane.
      </p>
      <pre aria-label="Pipeline configuration preview">{exported}</pre>
      <button onClick={() => downloadConfiguration(exported)} type="button">
        Download JSON configuration
      </button>
      <label className="textarea-field" htmlFor="pipeline-import">
        Import JSON configuration
        <textarea
          id="pipeline-import"
          onChange={(event) => props.onImportTextChange(event.target.value)}
          placeholder="Paste a previously exported configuration"
          value={props.importText}
        />
      </label>
      {props.importError ? (
        <p className="field-error" role="alert">
          {props.importError}
        </p>
      ) : null}
      <button onClick={props.onImport} type="button">
        Import JSON
      </button>
      <ValidationSummary
        errors={props.errors}
        message={props.validationMessage}
      />
      <button
        disabled={props.validating}
        onClick={props.onValidate}
        type="button"
      >
        {props.validating ? 'Validating…' : 'Validate configuration'}
      </button>
    </div>
  );
}

type SaveStepProps = Pick<StepFieldsProps, 'draft' | 'errors' | 'onChange'> & {
  onSave: () => void;
  saving: boolean;
  validationMessage: string | null;
};

function SaveStep({
  draft,
  errors,
  onChange,
  onSave,
  saving,
  validationMessage,
}: SaveStepProps) {
  return (
    <div className="review-stack">
      <TextField
        label="Pipeline name"
        name="pipeline-name"
        value={draft.name}
        onChange={(value) => onChange('name', value)}
        error={errorFor(errors, 'name')}
      />
      <label className="textarea-field" htmlFor="pipeline-description">
        Description
        <textarea
          id="pipeline-description"
          onChange={(event) => onChange('description', event.target.value)}
          value={draft.description}
        />
      </label>
      {validationMessage ? (
        <p className="status-message" role="status">
          {validationMessage}
        </p>
      ) : null}
      <button disabled={saving} onClick={onSave} type="button">
        {saving ? 'Saving…' : 'Save pipeline definition'}
      </button>
    </div>
  );
}

function ValidationSummary({
  errors,
  message,
}: {
  errors: ApiFieldViolation[];
  message: string | null;
}) {
  if (errors.length === 0 && message === null) return null;
  return (
    <div
      className={
        errors.length === 0
          ? 'validation-summary validation-summary--success'
          : 'validation-summary'
      }
      aria-live="polite"
    >
      {message ? <p>{message}</p> : null}
      {errors.length > 0 ? (
        <ul>
          {errors.map((error) => (
            <li key={`${error.field}-${error.message}`}>
              <strong>{error.field}:</strong> {error.message}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

function reducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'field':
      return {
        ...state,
        draft: { ...state.draft, [action.field]: action.value },
        fieldErrors: [],
        validationMessage: null,
      };
    case 'step':
      return {
        ...state,
        currentStep: Math.max(0, Math.min(action.step, steps.length - 1)),
      };
    case 'errors':
      return { ...state, fieldErrors: action.errors };
    case 'validation-message':
      return { ...state, validationMessage: action.message };
    case 'import':
      return {
        ...state,
        draft: action.draft,
        fieldErrors: [],
        validationMessage: null,
      };
  }
}

function toConfiguration(draft: PipelineDraft): PipelineConfiguration {
  const input: JsonObject = {
    type: 'STP_BINARY',
    path: draft.inputPath,
    source: draft.source,
    venue: draft.venue,
    maximumFrameSize: integerOrDefault(draft.maximumFrameSize, 49),
  };
  const transform: JsonObject =
    draft.transformation === 'RENAME_SYMBOL'
      ? {
          schemaVersion: '1.0',
          operations: [
            {
              op: 'rename',
              from: 'instrument.symbol',
              to: 'instrument.ticker',
            },
          ],
        }
      : {
          schemaVersion: '1.0',
          operations: [
            {
              op: 'add_constant',
              path: 'pipelineLabel',
              value: { type: 'STRING', value: 'streamforge' },
            },
          ],
        };
  const blueprint = canonicalEventBlueprint();
  return {
    input,
    transform,
    blueprint,
    output: { type: 'JSONL', path: draft.outputPath },
  };
}

function integerOrDefault(value: string, fallback: number): number {
  const number = Number(value);
  return Number.isInteger(number) ? number : fallback;
}

function errorFor(
  errors: ApiFieldViolation[],
  field: string,
): string | undefined {
  const match = errors.find(
    (error) =>
      error.field === field || error.field === `configuration.${field}`,
  );
  return match?.message;
}

function fieldErrors(error: unknown): ApiFieldViolation[] {
  if (
    typeof error === 'object' &&
    error !== null &&
    'fieldErrors' in error &&
    Array.isArray(error.fieldErrors)
  ) {
    return error.fieldErrors.filter(isFieldViolation);
  }
  return [];
}

function isFieldViolation(value: unknown): value is ApiFieldViolation {
  return (
    typeof value === 'object' &&
    value !== null &&
    'field' in value &&
    'message' in value &&
    typeof value.field === 'string' &&
    typeof value.message === 'string'
  );
}

function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : 'The control-plane request could not be completed.';
}

function moveToFirstError(
  errors: ApiFieldViolation[],
  dispatch: Dispatch<WizardAction>,
) {
  const field = errors[0]?.field;
  if (field?.includes('input')) dispatch({ type: 'step', step: 1 });
  else if (field?.includes('transform')) dispatch({ type: 'step', step: 2 });
  else if (field?.includes('blueprint')) dispatch({ type: 'step', step: 3 });
  else if (field?.includes('output')) dispatch({ type: 'step', step: 4 });
  else if (field === 'name' || field === 'description')
    dispatch({ type: 'step', step: 6 });
}

function downloadConfiguration(value: string) {
  const anchor = document.createElement('a');
  const url = URL.createObjectURL(
    new Blob([value], { type: 'application/json' }),
  );
  anchor.href = url;
  anchor.download = 'streamforge-pipeline.json';
  anchor.click();
  URL.revokeObjectURL(url);
}

function parseImportedDraft(value: string): PipelineDraft {
  const root = asRecord(JSON.parse(value));
  const configuration = asRecord(root.configuration);
  const input = asRecord(configuration.input);
  const output = asRecord(configuration.output);
  const transform = asRecord(configuration.transform);
  const blueprint = asRecord(configuration.blueprint);
  if (input.type !== 'STP_BINARY' || output.type !== 'JSONL') {
    throw new Error(
      'The imported input and output types are not supported by this guided flow.',
    );
  }
  if (JSON.stringify(blueprint) !== JSON.stringify(canonicalEventBlueprint())) {
    throw new Error(
      'The imported output blueprint is not supported by this guided flow.',
    );
  }
  return {
    name: optionalString(root.name),
    description: optionalString(root.description),
    inputPath: requiredString(input.path, 'input.path'),
    source: requiredString(input.source, 'input.source'),
    venue: requiredString(input.venue, 'input.venue'),
    maximumFrameSize: String(input.maximumFrameSize ?? 49),
    transformation: renamePreset(transform),
    blueprint: 'CANONICAL_EVENT',
    outputPath: requiredString(output.path, 'output.path'),
  };
}

function renamePreset(
  transform: Record<string, unknown>,
): TransformationPreset {
  const operations = transform.operations;
  if (!Array.isArray(operations) || operations.length !== 1) {
    throw new Error(
      'The imported transformation is not supported by this guided flow.',
    );
  }
  const first = operations[0];
  if (isRenameOperation(first)) return 'RENAME_SYMBOL';
  if (isAddPipelineLabelOperation(first)) return 'ADD_PIPELINE_LABEL';
  throw new Error(
    'The imported transformation is not supported by this guided flow.',
  );
}

function isRenameOperation(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) return false;
  const operation = value as Record<string, unknown>;
  return (
    operation.op === 'rename' &&
    operation.from === 'instrument.symbol' &&
    operation.to === 'instrument.ticker'
  );
}

function isAddPipelineLabelOperation(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) return false;
  const operation = value as Record<string, unknown>;
  if (
    operation.op !== 'add_constant' ||
    operation.path !== 'pipelineLabel' ||
    typeof operation.value !== 'object' ||
    operation.value === null
  ) {
    return false;
  }
  const typedValue = operation.value as Record<string, unknown>;
  return typedValue.type === 'STRING' && typedValue.value === 'streamforge';
}

function asRecord(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value))
    throw new Error('Imported configuration must be a JSON object.');
  return value as Record<string, unknown>;
}

function requiredString(value: unknown, path: string): string {
  if (typeof value !== 'string' || value.trim().length === 0)
    throw new Error(`Imported ${path} is required.`);
  return value;
}

function optionalString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function canonicalEventBlueprint(): JsonObject {
  return {
    schemaVersion: '1.0',
    output: {
      kind: 'object',
      fields: {
        eventId: {
          kind: 'reference',
          source: 'canonical',
          path: 'metadata.eventId',
        },
        type: { kind: 'reference', source: 'canonical', path: 'payload.type' },
        source: {
          kind: 'reference',
          source: 'canonical',
          path: 'metadata.source',
        },
        sequenceNumber: {
          kind: 'reference',
          source: 'canonical',
          path: 'metadata.sequenceNumber',
        },
      },
    },
  };
}
