import React from 'react';
import {capitalizeAndAddSpace} from "../util/string";

interface InputProps {
    name: string;
    submitted?: boolean;
    value?: string;
    handleChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
    type?: string;
};

const Input = (props: InputProps) => {

    const optionalValidationError = () => props.submitted && validationFailed(props.value) ? ' is-invalid' : ''
    const validationFailed = (field: string | any[]) => !field || field.length < 4

    return (
        <>
            <div className="form-group">
                <label>{capitalizeAndAddSpace(props.name)}</label>
                <input type={props.type} name={props.name} value={props.value} onChange={props.handleChange}
                       className={'form-control' + optionalValidationError()} />
                {optionalValidationError() &&
                <div className="invalid-feedback">Required field length is 4 or more</div>
                }
            </div>
        </>
    );
};


export {Input};