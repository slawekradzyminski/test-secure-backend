import React, { ChangeEvent } from 'react';
import {capitalizeAndAddSpace} from "../util/string";

interface TextareaProps {
    name: string;
    submitted?: boolean;
    value: string;
    handleChange: (event: ChangeEvent<HTMLTextAreaElement>) => void;
}

const Textarea = (props: TextareaProps) => {

    const optionalValidationError = () => props.submitted && validationFailed(props.value) ? ' is-invalid' : ''
    const validationFailed = (field: string | any[]) => !field || field.length < 4

    return (
        <>
            <div className="form-group">
                <label>{capitalizeAndAddSpace(props.name)}</label>
                <textarea name={props.name} value={props.value} onChange={props.handleChange}
                       className={'form-control' + optionalValidationError()} />
                {optionalValidationError() &&
                <div className="invalid-feedback">Required field length is 4 or more</div>
                }
            </div>
        </>
    );
};

export {Textarea};