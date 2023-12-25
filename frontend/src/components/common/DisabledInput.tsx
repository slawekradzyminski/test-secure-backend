import React from 'react';
import {capitalizeAndAddSpace} from "../util/string";

interface DisabledInputProps {
    name: string;
    value: string;
}

const DisabledInput = (props: DisabledInputProps) => {
    return (
        <>
            <div className="form-group">
                <label>{capitalizeAndAddSpace(props.name)}</label>
                <input disabled={true} type="text" name={props.name} value={props.value} className={'form-control'}/>
            </div>
        </>
    );
};

export {DisabledInput};
